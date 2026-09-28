# Retrofit / Moshi model classes are kept via @JsonClass(codegen = true) — no reflection.
# Room entities/DAOs are referenced by generated code and are kept automatically.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
