# Keep the Compose internals class name. We need this in the SR recorder.
-keepnames class androidx.compose.runtime.Anchor
-keepnames class androidx.compose.runtime.ComposerImpl$CompositionContextHolder
-keepnames class androidx.compose.runtime.ComposerImpl$CompositionContextImpl
-keepnames class androidx.compose.runtime.CompositionImpl
-keepnames class androidx.compose.runtime.RecomposeScopeImpl
-keepnames class androidx.compose.runtime.Composition
-keepnames class androidx.compose.ui.platform.ComposeView
-keepnames class androidx.compose.material.DefaultButtonColors
-keepclassmembers class androidx.compose.ui.platform.WrappedComposition {
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
