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
-keepclassmembers class androidx.compose.ui.semantics.SemanticsNode {
     <fields>;
}
-keepclassmembers class androidx.compose.ui.draw.PainterElement {
     <fields>;
}
-keepclassmembers class androidx.compose.ui.graphics.vector.VectorPainter {
     <fields>;
}
-keepclassmembers class androidx.compose.ui.graphics.painter.BitmapPainter {
     <fields>;
}
-keepclassmembers class androidx.compose.ui.graphics.vector.VectorComponent {
     <fields>;
}
-keepclassmembers class androidx.compose.ui.graphics.vector.DrawCache {
     <fields>;
}
-keepclassmembers class androidx.compose.ui.graphics.AndroidImageBitmap {
     <fields>;
}
-keep class coil.compose.ContentPainterModifier {
     <fields>;
}
-keep class coil.compose.AsyncImagePainter {
     <fields>;
}
-keepclassmembers class androidx.compose.foundation.layout.PaddingElement{
    <fields>;
}
-keepclassmembers class "androidx.compose.ui.graphics.GraphicsLayerElement"{
    <fields>;
}

