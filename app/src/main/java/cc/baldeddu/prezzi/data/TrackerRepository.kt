package cc.baldeddu.prezzi.data

import android.util.AtomicFile
import cc.baldeddu.prezzi.BuildConfig
import cc.baldeddu.prezzi.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class Snapshot(val data: TrackerData, val fetchedAt: Long, val warning: String? = null)
data class PurchaseReceipt(val product: Product, val observation: Observation, val created: Boolean)

interface TrackerGateway {
    suspend fun cached(): Snapshot?
    suspend fun refresh(): Snapshot
    suspend fun purchase(draft: PurchaseDraft, data: TrackerData): PurchaseReceipt
    suspend fun delete(productId: Int)
}

fun interface Transport {
    fun request(method: String, path: String, body: JSONObject?): JSONObject
}

class ApiException(message: String) : IOException(message)

class HttpTransport(private val baseUrl: String = BuildConfig.API_BASE_URL) : Transport {
    override fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = method
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use(::readBounded) ?: byteArrayOf()
            val json = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            if (status !in 200..299) throw ApiException(json?.optString("error")?.takeIf { it.isNotBlank() }
                ?: "Il server ha risposto con errore $status.")
            return json ?: throw ApiException(if (method == "GET") "Il server ha restituito dati non leggibili."
                else "Risposta non leggibile: esito non confermato. Aggiorna il paniere prima di riprovare, per evitare duplicati.")
        } finally {
            connection.disconnect()
        }
    }
}

private fun readBounded(stream: InputStream): ByteArray {
    val limit = 8 * 1024 * 1024
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw ApiException("Risposta troppo grande. Contatta chi gestisce il server.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

object TrackerJson {
    fun product(json: JSONObject): Product {
        val product = Product(json.getInt("id"), json.getString("name"), json.optString("brand", ""),
            json.getString("unit"), json.getDouble("basketQuantity"))
        require(product.id > 0 && product.name.isNotBlank() && product.basketQuantity.isFinite()
            && product.basketQuantity > 0 && product.unit in listOf("kg", "L", "pezzo")) { "Prodotto non valido." }
        return product
    }

    fun observation(json: JSONObject): Observation {
        fun optional(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }
        return Observation(json.getInt("id"), json.getInt("productId"), json.getInt("unitPriceCents"),
            LocalDate.parse(json.getString("observedOn")), optional("location"), optional("note")).also {
            require(it.id > 0 && it.productId > 0 && it.unitPriceCents in 1..100_000_000) { "Prezzo non valido." }
        }
    }

    fun decode(json: JSONObject): TrackerData {
        val products = json.getJSONArray("products")
        val observations = json.getJSONArray("observations")
        val data = TrackerData((0 until products.length()).map { product(products.getJSONObject(it)) },
            (0 until observations.length()).map { observation(observations.getJSONObject(it)) })
        val ids = data.products.map { it.id }.toSet()
        require(ids.size == data.products.size && data.observations.map { it.id }.toSet().size == data.observations.size)
        require(data.observations.all { it.productId in ids }) { "Un prezzo appartiene a un prodotto assente." }
        return data
    }

    fun encode(data: TrackerData): JSONObject = JSONObject().put("products", JSONArray().apply {
        data.products.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("brand", it.brand)
            .put("unit", it.unit).put("basketQuantity", it.basketQuantity)) }
    }).put("observations", JSONArray().apply {
        data.observations.forEach { put(JSONObject().put("id", it.id).put("productId", it.productId)
            .put("unitPriceCents", it.unitPriceCents).put("observedOn", it.observedOn.toString())
            .put("location", it.location ?: JSONObject.NULL).put("note", it.note ?: JSONObject.NULL)) }
    })

    fun purchaseBody(draft: PurchaseDraft, data: TrackerData): JSONObject {
        require(draft.errors(data).isEmpty()) { "Correggi i campi indicati prima di salvare." }
        val existing = data.findProduct(draft.name, draft.brand)
        return JSONObject().put("name", draft.name.trim()).put("brand", draft.brand.trim())
            .put("unit", existing?.unit ?: draft.unit)
            .put("basketQuantity", existing?.basketQuantity ?: draft.quantity.replace(',', '.').toDouble())
            .put("unitPrice", draft.price.replace(',', '.')).put("observedOn", draft.date)
            .put("location", draft.location.trim()).put("note", draft.note.trim())
    }
}

class TrackerRepository(cacheFile: File, private val transport: Transport = HttpTransport()) : TrackerGateway {
    private val cacheFile = AtomicFile(cacheFile)

    override suspend fun cached(): Snapshot? = withContext(Dispatchers.IO) {
        if (!cacheFile.baseFile.exists()) return@withContext null
        val json = cacheFile.openRead().use { JSONObject(readBounded(it).toString(Charsets.UTF_8)) }
        require(json.getInt("version") == 1) { "Versione cache non compatibile." }
        Snapshot(TrackerJson.decode(json.getJSONObject("data")), json.getLong("fetchedAt"))
    }

    override suspend fun refresh(): Snapshot = withContext(Dispatchers.IO) {
        val data = try {
            TrackerJson.decode(transport.request("GET", "/api/tracker", null))
        } catch (error: ApiException) { throw error }
        catch (error: IOException) { throw ApiException("Connessione non disponibile. Gli ultimi dati caricati restano consultabili.") }
        catch (error: Exception) { throw ApiException("I dati ricevuti non sono validi. Riprova ad aggiornare.") }
        val fetchedAt = System.currentTimeMillis()
        val warning = runCatching { writeCache(data, fetchedAt) }.exceptionOrNull()?.let {
            "Dati aggiornati, ma la cache sul dispositivo non è stata salvata."
        }
        Snapshot(data, fetchedAt, warning)
    }

    private fun writeCache(data: TrackerData, fetchedAt: Long) {
        val bytes = JSONObject().put("version", 1).put("fetchedAt", fetchedAt)
            .put("data", TrackerJson.encode(data)).toString().toByteArray(Charsets.UTF_8)
        val stream = cacheFile.startWrite()
        try {
            stream.write(bytes)
            cacheFile.finishWrite(stream)
        } catch (error: Exception) {
            cacheFile.failWrite(stream)
            throw error
        }
    }

    override suspend fun purchase(draft: PurchaseDraft, data: TrackerData): PurchaseReceipt = withContext(Dispatchers.IO) {
        val body = TrackerJson.purchaseBody(draft, data)
        val response = mutate("POST", "/api/products", body)
        try {
            PurchaseReceipt(TrackerJson.product(response.getJSONObject("product")),
                TrackerJson.observation(response.getJSONObject("observation")), response.getBoolean("created"))
        } catch (error: Exception) {
            throw ApiException("Il server non ha confermato l’esito. Aggiorna il paniere prima di riprovare, per evitare duplicati.")
        }
    }

    override suspend fun delete(productId: Int): Unit = withContext(Dispatchers.IO) {
        require(productId > 0)
        mutate("DELETE", "/api/products/$productId", null)
        // A pre-mutation cache must never reappear as if it were current after a restart.
        cacheFile.delete()
    }

    private fun mutate(method: String, path: String, body: JSONObject?): JSONObject = try {
        val result = transport.request(method, path, body)
        cacheFile.delete()
        result
    } catch (error: ApiException) { throw error }
    catch (error: IOException) {
        throw ApiException("Connessione interrotta: esito non confermato. Aggiorna il paniere prima di riprovare, per evitare duplicati.")
    }
}
