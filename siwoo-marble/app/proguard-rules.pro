# 리플렉션을 쓰지 않으므로 별도 keep 규칙이 거의 필요 없다.
# Activity 등은 AGP가 매니페스트를 보고 자동으로 보존한다.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
}
