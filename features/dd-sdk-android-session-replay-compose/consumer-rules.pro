# Keep the Compose internals class name. We need this in the SR recorder.
-keepnames class androidx.compose.ui.platform.ComposeView
-keepnames class androidx.compose.material.DefaultButtonColors
-keepclassmembers class androidx.compose.foundation.text.modifiers.TextStringSimpleElement {
     <fields>;
}
-keep class androidx.compose.material.CheckDrawingCache {
     <fields>;
}
-keep class androidx.compose.material.CheckboxKt$CheckboxImpl$1$1 {
     <fields>;
}
-keep class androidx.compose.material.RadioButtonKt$RadioButton$2$1 {
     <fields>;
}
-keep class androidx.compose.ui.draw.DrawBehindElement {
     <fields>;
}
# Used to classify drawing modifiers for composition-tree pixel capture.
-keep class androidx.compose.ui.draw.DrawWithContentElement
-keep class androidx.compose.ui.draw.DrawWithCacheElement
# Optional recognition of the platform overscroll modifier when all eight edge effects are finished.
-keep,allowoptimization class androidx.compose.foundation.DrawOverscrollModifier {
    <fields>;
}
-keep,allowoptimization class androidx.compose.foundation.AndroidEdgeEffectOverscrollEffect {
    <fields>;
}
-keepclassmembers class androidx.compose.foundation.BackgroundElement {
     <fields>;
}
-keep class androidx.compose.ui.node.LayoutNode {
     *;
}
-keep class androidx.compose.ui.node.NodeChain {
    <fields>;
}
-keep class androidx.compose.ui.Modifier$Node{
    <fields>;
}
-keep class coil3.compose.internal.ContentPainterNode{
    <fields>;
}
-keep class coil3.compose.AsyncImagePainter{
    <methods>;
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

# Optional invalidation checks for the measured plain-text paragraph cache.
-keep,allowoptimization class androidx.compose.foundation.text.modifiers.TextStringSimpleNode {
    *** _layoutCache;
    *** semanticsTextLayoutResult;
}
-keep,allowoptimization class androidx.compose.foundation.text.modifiers.ParagraphLayoutCache {
    *** paragraph;
    *** paragraphIntrinsics;
    long prevConstraints;
    long layoutSize;
}
