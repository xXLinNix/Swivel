# Shizuku starts the virtual-pad service in its own process by reflection on this
# class name and its no-argument constructor, so R8 must keep both.
-keep class io.github.xxlinnix.swivel.data.virtualpad.VirtualPadUserService {
    <init>();
}
