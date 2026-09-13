package com.zerium.browser;

import android.content.Context;
import org.json.JSONArray;
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
        return Utils.readAssetLines(c, "blocklists/cosmetic.txt");
    }

    static String buildScript(List<String> selectors) {
        JSONArray arr = new JSONArray();
        for (String sel : selectors) {
            if (sel.length() < 3 || sel.length() > 200) continue;
            if (!SAFE_SELECTOR.matcher(sel).matches()) continue;
            arr.put(sel);
        }
        String json = arr.toString();
        StringBuilder js = new StringBuilder();
        js.append("(function(){")
          .append("var sels=").append(json).append(";")
          .append("if(!sels||!sels.length)return;")
          .append("var pending=false;")
          .append("function apply(){pending=false;")
          .append("for(var i=0;i<sels.length;i++){try{")
          .append("var nodes=document.querySelectorAll(sels[i]);")
          .append("for(var j=0;j<nodes.length;j++){")
          .append("nodes[j].style.setProperty('display','none','important');")
          .append("nodes[j].setAttribute('data-zerium-hidden','1');")
          .append("}}catch(e){}}}")
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
