package components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders [html] against [baseUrl], used by the resolver to play YouTube and
 * Instagram media in place.
 *
 * The document is framed rather than loaded directly because both providers
 * reject their embed pages when opened as a top-level document.
 */
@Composable
expect fun InlineWebView(
    html: String,
    baseUrl: String,
    modifier: Modifier = Modifier,
)

/** Full-bleed page wrapping [src] in an iframe sized to the viewport. */
fun inlineEmbedHtml(src: String): String = """
    <!DOCTYPE html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
        <style>
          html, body { margin:0; padding:0; height:100%; background:#000; overflow:hidden; }
          iframe { display:block; border:0; width:100%; height:100%; }
        </style>
      </head>
      <body>
        <iframe
          src="$src"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          allowfullscreen>
        </iframe>
      </body>
    </html>
""".trimIndent()
