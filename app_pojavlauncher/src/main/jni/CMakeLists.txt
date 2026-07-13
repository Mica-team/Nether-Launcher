
cmake_minimum_required(VERSION 3.22.1)

project("MojoLauncher")

add_subdirectory(mojoexec)

add_library(vmloader SHARED
        affinity.c
        jvm_hooks/emui_iterator_fix_hook.c
        jvm_hooks/java_exec_hooks.c
        jvm_hooks/lwjgl_dlopen_hook.c
        jre_launcher/jre_launcher.c
        jre_launcher/native_library_hook.c
        jre_launcher/elf_hinter.c
        jre_launcher/abort_wait.c
        utils.c
        stdio_is.c
        minibridge.c
)

target_include_directories(vmloader PRIVATE .)
target_link_libraries(vmloader PRIVATE log mojoexec)

add_library(pojavexec_awt SHARED
        awt_bridge.c
)

add_library(awt_headless SHARED 
	awt_xawt/dummy.c
)

add_library(awt_xawt SHARED
        awt_xawt/xawt_fake.c
)
target_link_libraries(awt_xawt PRIVATE awt_headless)