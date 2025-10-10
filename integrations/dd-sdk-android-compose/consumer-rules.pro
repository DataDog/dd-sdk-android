# Keep the Compose internals class name. We need this in the RUM actions tracking.
-keep class androidx.compose.foundation.ClickableElement {
    <fields>;
}
-keep class androidx.compose.foundation.CombinedClickableElement {
    <fields>;
}
