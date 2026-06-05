package luzzr.muse.player

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class FloatingLyricsContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun performClick(): Boolean = super.performClick()
}
