# Keep the Compose internals class name. We need this in the SR recorder.
-keepnames class androidx.compose.runtime.Composition
-keepnames class androidx.compose.ui.platform.ComposeView
-keepnames class androidx.compose.material.DefaultButtonColors
-keep class androidx.compose.ui.platform.WrappedComposition {
    <fields>;
}
-keepclassmembers class androidx.compose.ui.platform.AbstractComposeView {
    <fields>;
}
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
     <fields>;
}
-keepclassmembers class androidx.compose.foundation.text.modifiers.TextStringSimpleElement {
     <fields>;
}
-keepclassmembers class androidx.compose.foundation.BackgroundElement {
     <fields>;
}
-keep class androidx.compose.ui.node.LayoutNode {
     *;
}
-keep class androidx.compose.ui.semantics.SemanticsNode {
     <fields>;
}
-keep class androidx.compose.ui.draw.PainterElement {
     <fields>;
}
-keep class androidx.compose.ui.graphics.vector.VectorPainter {
     <fields>;
}
-keep class androidx.compose.ui.graphics.painter.BitmapPainter {
     <fields>;
}
-keep class androidx.compose.ui.graphics.vector.VectorComponent {
     <fields>;
}
-keep class androidx.compose.ui.graphics.vector.DrawCache {
     <fields>;
}
-keep class androidx.compose.ui.graphics.AndroidImageBitmap {
     <fields>;
}
-keep class coil.compose.ContentPainterElement {
     <fields>;
}
-keep class coil.compose.ContentPainterModifier {
     <fields>;
}
-keep class coil.compose.AsyncImagePainter {
     <fields>;
}
-keep class androidx.compose.foundation.layout.PaddingElement{
    <fields>;
}
-keep class androidx.compose.ui.graphics.GraphicsLayerElement{
    <fields>;
}
-keep class androidx.compose.ui.text.ParagraphInfo{
    <fields>;
}
-keep class androidx.compose.ui.text.AndroidParagraph{
    <fields>;
}
-keep class androidx.compose.ui.text.android.TextLayout{
    <fields>;
}
