// Regenerate expected results from the original application, never from the Android port.
import { pathToFileURL } from "node:url";
import { resolve } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";

const source = resolve(process.argv[2] ?? "../personal-inflation");
const { comparisonWindows, inflationFor } = await import(pathToFileURL(resolve(source, "lib/inflation.ts")));
const { basketPriceHistory, productPriceHistory, basketYearOverYear } = await import(pathToFileURL(resolve(source, "lib/price-history.ts")));
let seed = 20261023;
const random = (max) => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed % max; };
const cases = Array.from({ length: 60 }, (_, index) => {
  const products = Array.from({ length: index % 6 }, (_, i) => ({
    id: i + 1, name: `Prodotto ${i}`, brand: `Marca ${i % 2}`, unit: "kg", basketQuantity: (random(999) + 1) / 100,
  }));
  const observations = products.flatMap((product) => Array.from({ length: random(20) }, () => ({
    productId: product.id, unitPriceCents: random(100000) + 1,
    observedOn: new Date(Date.UTC(2020, 0, 1 + random(2400))).toISOString().slice(0, 10),
  }))).map((entry, i) => ({ ...entry, id: i + 1 }));
  const data = { products, observations };
  const anchor = observations.map((e) => e.observedOn).sort().at(-1) ?? null;
  const points = basketPriceHistory(data).points;
  return { data, metrics: comparisonWindows.map((w) => inflationFor(data, w.duration, anchor)),
    points, annual: basketYearOverYear(points),
    productPoints: products.map((p) => productPriceHistory(p, observations)) };
});
const output = resolve("app/src/test/resources/parity.json");
mkdirSync(resolve(output, ".."), { recursive: true });
writeFileSync(output, JSON.stringify({ seed: 20261023, source: "personal-inflation/lib/inflation.ts + lib/price-history.ts", cases }));
console.log(`Generated ${cases.length} reference cases at ${output}`);
