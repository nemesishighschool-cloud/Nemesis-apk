package com.nemap.android.core

/** Never mistake the previous assistant turn for the current one in DOM fallback. */
object DomFreshness {
    fun isFresh(ok: Boolean, text: String, blocks: Int, oldText: String, oldBlocks: Int): Boolean =
        ok && text.isNotBlank() && (blocks > oldBlocks || text != oldText)

    /** Extract only the new part when a continuation extends the same block. */
    fun freshText(ok:Boolean, text:String, blocks:Int, oldText:String, oldBlocks:Int, continuation:Boolean): String {
        if (!isFresh(ok,text,blocks,oldText,oldBlocks)) return ""
        if (continuation && blocks == oldBlocks) {
            return if(oldText.isNotEmpty() && text.startsWith(oldText)) text.substring(oldText.length) else ""
        }
        return text
    }
}
