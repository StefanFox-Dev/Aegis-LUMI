plugins {
    base
}

allprojects {
    group = "top.xorek.aegis"
    version = "1.0.0"
}

tasks.register("jar") {
    dependsOn(":bridge:jar")
}
