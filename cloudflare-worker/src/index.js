const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Access-Control-Max-Age": "86400",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...corsHeaders },
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    const url = new URL(request.url);
    if (url.pathname !== "/chat" || request.method !== "POST") {
      return json({ error: "Gunakan POST /chat." }, 404);
    }

    // Optional shared access token. It is not a provider API key.
    // Add it with: wrangler secret put APP_ACCESS_TOKEN
    if (env.APP_ACCESS_TOKEN) {
      const supplied = request.headers.get("Authorization") || "";
      if (supplied !== `Bearer ${env.APP_ACCESS_TOKEN}`) {
        return json({ error: "Token akses tidak cocok." }, 401);
      }
    }

    const length = Number(request.headers.get("Content-Length") || 0);
    if (length > 9_000_000) return json({ error: "Permintaan terlalu besar. Coba tangkap layar lagi." }, 413);

    let input;
    try {
      input = await request.json();
    } catch {
      return json({ error: "Format JSON tidak valid." }, 400);
    }

    const provider = String(input.provider || "gemini").toLowerCase();
    const prompt = String(input.prompt || "").trim();
    const imageBase64 = typeof input.imageBase64 === "string" ? input.imageBase64 : "";
    const mimeType = String(input.mimeType || "image/jpeg");

    if (!prompt) return json({ error: "Pertanyaan masih kosong." }, 400);
    if (prompt.length > 12000) return json({ error: "Pertanyaan terlalu panjang." }, 413);
    if (imageBase64.length > 8_000_000) return json({ error: "Gambar terlalu besar." }, 413);
    if (imageBase64 && !["image/jpeg", "image/png", "image/webp"].includes(mimeType)) {
      return json({ error: "Jenis gambar tidak didukung." }, 400);
    }

    try {
      if (provider === "gemini") {
        if (!env.GEMINI_API_KEY) return json({ error: "Secret GEMINI_API_KEY belum dipasang di Cloudflare Worker." }, 503);
        const parts = [{ text: prompt }];
        if (imageBase64) parts.push({ inline_data: { mime_type: mimeType, data: imageBase64 } });

        const response = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              contents: [{ role: "user", parts }],
              generationConfig: { temperature: 0.4, maxOutputTokens: 2400 },
            }),
          },
        );
        const data = await response.json();
        if (!response.ok) {
          const message = data?.error?.message || `Gemini mengembalikan HTTP ${response.status}`;
          return json({ error: message }, response.status >= 400 && response.status < 500 ? 400 : 502);
        }
        const answer = (data?.candidates?.[0]?.content?.parts || [])
          .map((part) => part.text || "")
          .join("")
          .trim();
        if (!answer) return json({ error: "Gemini tidak mengirim teks jawaban." }, 502);
        return json({ ok: true, provider: "gemini", answer });
      }

      if (provider === "deepseek") {
        if (!env.DEEPSEEK_API_KEY) return json({ error: "Secret DEEPSEEK_API_KEY belum dipasang di Cloudflare Worker." }, 503);
        const content = [{ type: "text", text: prompt }];
        if (imageBase64) {
          content.push({
            type: "image_url",
            image_url: { url: `data:${mimeType};base64,${imageBase64}`, detail: "high" },
          });
        }
        const response = await fetch("https://api.deepseek.com/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${env.DEEPSEEK_API_KEY}`,
          },
          body: JSON.stringify({
            model: "deepseek-v4-flash",
            messages: [{ role: "user", content }],
            max_tokens: 2400,
            temperature: 0.4,
            thinking: { type: "disabled" },
          }),
        });
        const data = await response.json();
        if (!response.ok) {
          const message = data?.error?.message || `DeepSeek mengembalikan HTTP ${response.status}`;
          return json({ error: message }, response.status >= 400 && response.status < 500 ? 400 : 502);
        }
        const answer = data?.choices?.[0]?.message?.content;
        if (typeof answer !== "string" || !answer.trim()) {
          return json({ error: "DeepSeek tidak mengirim teks jawaban." }, 502);
        }
        return json({ ok: true, provider: "deepseek", answer: answer.trim() });
      }

      return json({ error: "Provider tidak dikenal. Pilih gemini atau deepseek." }, 400);
    } catch (error) {
      return json({ error: `Gateway gagal memproses permintaan: ${error?.message || "unknown error"}` }, 502);
    }
  },
};
