const baseUrl = process.env.HAOBLOG_BASE_URL;
if (!baseUrl) throw new Error('HAOBLOG_BASE_URL is required');

const base = new URL(baseUrl);
const request = async (path) => {
  const response = await fetch(new URL(path, base));
  const text = await response.text();
  return { response, text };
};

const expectStatus = async (path, expected) => {
  const { response, text } = await request(path);
  if (response.status !== expected) throw new Error(`${path}: expected ${expected}, got ${response.status}: ${text.slice(0, 200)}`);
  return text;
};

await expectStatus('/', 200);
await expectStatus('/api/v1/public/site', 200);
const articles = JSON.parse(await expectStatus('/api/v1/public/articles', 200));
if (articles.items?.[0]?.slug) {
  const slug = encodeURIComponent(articles.items[0].slug);
  const articleHtml = await expectStatus(`/articles/${slug}`, 200);
  for (const marker of [articles.items[0].title, 'meta name="description"', 'property="og:title"', '<article']) {
    if (!articleHtml.includes(marker)) throw new Error(`SSR article HTML is missing: ${marker}`);
  }
  await expectStatus('/articles/not-published', 404);
}
for (const path of ['/actuator', '/actuator/health']) {
  const { response } = await request(path);
  if (response.status >= 200 && response.status < 300) throw new Error(`${path}: actuator is publicly reachable`);
}
console.log('Smoke test passed');
