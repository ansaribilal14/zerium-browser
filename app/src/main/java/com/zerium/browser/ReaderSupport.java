package com.zerium.browser;

import android.content.Context;

/**
 * Builds the Reader view toggle scripts.
 *
 * Reader mode bundles Mozilla's Readability (v0.6.0, Apache-2.0 — see the
 * header inside assets/readability.js). The library source is injected as part
 * of the same evaluateJavascript call as the toggle logic, wrapped in an IIFE:
 * page CSP does not apply to evaluateJavascript (unlike script-tag injection),
 * and function declarations inside the IIFE are grabbed into a window handle
 * so repeated invocations never re-declare anything.
 *
 * The reader is a client-side *presentation* toggle: the original DOM snapshot
 * is cached on the page itself and restored on toggle-off. It parses whatever
 * the WebView currently holds — paywalled pages yield whatever the page had,
 * nothing is re-fetched or faked.
 */
public final class ReaderSupport {

    private static final String READER_CSS =
            "#zeriumReaderMain{max-width:680px;margin:0 auto;padding:28px 20px 72px;}"
            + "#zeriumReaderMain h1{font-size:1.65em;line-height:1.25;margin:0 0 6px;}"
            + "#zeriumReaderMain .zrMeta{opacity:.65;font-size:.85em;margin:0 0 22px;"
            + "font-family:system-ui,sans-serif;}"
            + "#zeriumReaderMain .zrContent{font-size:1.05em;line-height:1.65;word-wrap:break-word;}"
            + "#zeriumReaderMain .zrContent img,#zeriumReaderMain .zrContent video,"
            + "#zeriumReaderMain .zrContent table{max-width:100%;height:auto;}"
            + "#zeriumReaderMain .zrContent pre{overflow-x:auto;white-space:pre-wrap;}"
            + "#zeriumReaderMain .zrContent a{color:#4355b9;}"
            + "body{background:#f6f7fb;color:#1b1b1f;}"
            + "@media (prefers-color-scheme:dark){"
            + "body{background:#0e1016;color:#e4e2e6;}"
            + "#zeriumReaderMain .zrContent a{color:#b6c4ff;}}";

    private static volatile String readabilitySource;

    private ReaderSupport() {}

    private static String source(Context c) {
        String s = readabilitySource;
        if (s == null) {
            s = Utils.readAsset(c, "readability.js");
            readabilitySource = s;
        }
        return s;
    }

    /** Script that enters reader mode. Returns a JSON object via the JS callback. */
    public static String onScript(Context c) {
        // The readability source is declared inside the IIFE; on first run the
        // function declaration binds locally and is stashed on the window so
        // later invocations reuse it without re-declaring.
        StringBuilder js = new StringBuilder();
        js.append("(function(){try{");
        js.append("if(!window.__zeriumReadability){");
        js.append(source(c));
        js.append("window.__zeriumReadability=Readability;}");
        js.append("var R=window.__zeriumReadability;");
        js.append("if(window.__zeriumReaderOriginal){return{ok:3};}");
        js.append("var article=new R(document.cloneNode(true),{charThreshold:200}).parse();");
        js.append("if(!article||!article.textContent||article.textContent.trim().length<250){return{ok:0};}");
        js.append("var words=article.textContent.trim().split(/\\s+/).length;");
        js.append("var minutes=Math.max(1,Math.round(words/265));");
        js.append("window.__zeriumReaderOriginal=document.documentElement.outerHTML;");
        js.append("var css=document.createElement('style');css.id='zeriumReaderCss';");
        js.append("css.textContent='").append(READER_CSS).append("';");
        js.append("(document.head||document.documentElement).appendChild(css);");
        js.append("var main=document.createElement('main');main.id='zeriumReaderMain';");
        js.append("var h=document.createElement('h1');");
        js.append("h.textContent=article.title||document.title||'';main.appendChild(h);");
        js.append("var meta=document.createElement('div');meta.className='zrMeta';");
        js.append("var bits=[];if(article.byline){bits.push(article.byline);}");
        js.append("bits.push('~'+minutes+' min read');");
        js.append("meta.textContent=bits.join(' \\u00b7 ');main.appendChild(meta);");
        js.append("var body=document.createElement('div');body.className='zrContent';");
        js.append("body.innerHTML=article.content||'';main.appendChild(body);");
        js.append("document.body.innerHTML='';document.body.appendChild(main);");
        js.append("window.scrollTo(0,0);");
        js.append("return{ok:1,minutes:minutes};");
        js.append("}catch(e){return{ok:0};}})();");
        return js.toString();
    }

    /** Script that leaves reader mode (restores the cached original DOM). */
    public static String offScript() {
        return "(function(){try{"
                + "if(window.__zeriumReaderOriginal){"
                + "document.documentElement.innerHTML=window.__zeriumReaderOriginal;"
                + "window.__zeriumReaderOriginal=null;window.scrollTo(0,0);return{ok:2};}"
                + "return{ok:0};}catch(e){return{ok:0};}})();";
    }
}
