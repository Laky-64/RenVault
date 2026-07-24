import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";
import wails from "@wailsio/runtime/plugins/vite";
import { paraglideVitePlugin } from "@inlang/paraglide-js";

// https://vitejs.dev/config/
export default defineConfig({
  server: {
    host: "127.0.0.1",
    port: Number(process.env.WAILS_VITE_PORT) || 9245,
    strictPort: true,
    watch: { usePolling: true },
  },
  plugins: [
    svelte(),
    wails("./bindings"),
    paraglideVitePlugin({
      project: "./project.inlang",
      outdir: "./src/paraglide",
      emitTsDeclarations: true,
      strategy: ["localStorage", "preferredLanguage", "baseLocale"],
    }),
  ],
});
