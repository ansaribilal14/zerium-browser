package com.zerium.browser;

import android.content.Context;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cosmetic (element-hiding) filtering. Loads curated generic CSS selectors from
 * assets and injects a script that hides matching nodes, including nodes added
 * later by dynamic page scripts.
 */
public class CosmeticFilter {

    // Selectors must be plain CSS: letters, digits, and attribute/class syntax only.
    private static final Pattern SAFE_SELECTOR =
            Pattern.compile("^[A-Za-z0-9_\\-.\\[\\]\\^$\\*=\"'#~:()>+,|\\s]+$");

    private static volatile String cachedScript = null;

    public static String getScript(Context c, boolean enabled) {
        if (!enabled) return null;
        String s = cachedScript;
        if (s == null) {
            s = buildScript(loadSelectors(c));
            cachedScript = s;
        }
        return s;
    }

    /** Called after the user edits/updates nothing built-in; kept for future dynamic lists. */
    public static void invalidate() { cachedScript = null; }

    static List<String> loadSelectors(Context c) {
        // A successfully validated downloaded list (FilterUpdater) wins over
        // the shipped snapshot; falls back to the bundled asset otherwise.
        try {
            File updated = new File(c.getFilesDir(), FilterUpdater.COSMETIC_FILE);
            if (updated.exists() && updated.length() > 0) {
                List<String> fromFile = readFileLines(updated);
                if (!fromFile.isEmpty()) return fromFile;
            }
        } catch (Exception ignored) {}
        return Utils.readAssetLines(c, "blocklists/cosmetic.txt");
    }

    private static List<String> readFileLines(File f) {
        List<String> out = new ArrayList<>();
        try (InputStream is = new FileInputStream(f)) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("!")) out.add(line);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static String buildScript(List<String> selectors) {
        JSONArray arr = new JSONArray();
        for (String sel : selectors) {
            if (sel.length() < 3 || sel.length() > 200) continue;
            if (!SAFE_SELECTOR.matcher(sel).matches()) continue;
            arr.put(sel);
        }
        String json = arr.toString();
        // Selectors are queried in comma-joined batches (one engine pass per
        // batch instead of one pass per selector — the list is large enough to
        // matter on mutation-heavy pages). If a batch fails to parse as a whole
        // (e.g. a :has() on an old engine), it is retried selector by selector.
        StringBuilder js = new StringBuilder();
        js.append("(function(){")
          .append("var sels=").append(json).append(";")
          .append("if(!sels||!sels.length)return;")
          .append("var BATCH=60,groups=[];")
          .append("for(var i=0;i<sels.length;i+=BATCH){groups.push(sels.slice(i,i+BATCH));}")
          .append("var pending=false;")
          .append("function hide(el){el.style.setProperty('display','none','important');")
          .append("el.setAttribute('data-zerium-hidden','1');}")
          .append("function applyGroup(g){try{")
          .append("var nodes=document.querySelectorAll(g.join(','));")
          .append("for(var j=0;j<nodes.length;j++)hide(nodes[j]);")
          .append("}catch(e){for(var k=0;k<g.length;k++){try{")
          .append("var n2=document.querySelectorAll(g[k]);")
          .append("for(var m=0;m<n2.length;m++)hide(n2[m]);")
          .append("}catch(e2){}}}}")
          .append("function apply(){pending=false;")
          .append("for(var i=0;i<groups.length;i++)applyGroup(groups[i]);}")
          .append("function schedule(){if(!pending){pending=true;")
          .append("if(window.requestAnimationFrame){requestAnimationFrame(apply);}else{apply();}}}")
          .append("if(document.readyState==='loading'){")
          .append("document.addEventListener('DOMContentLoaded',schedule);}else{schedule();}")
          .append("window.addEventListener('load',schedule);")
          .append("try{var mo=new MutationObserver(schedule);")
          .append("function startMo(){mo.observe(document.documentElement,{childList:true,subtree:true});}")
          .append("if(document.documentElement){startMo();}")
          .append("else{document.addEventListener('DOMContentLoaded',startMo);}}catch(e){}")
          .append("})();");
        return js.toString();
    }
}
