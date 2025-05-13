plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    id("java")
    id("application")
}

group = "com.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.example.Main")
}

dependencies {
    implementation("fr.inria.gforge.spoon.labs:gumtree-spoon-ast-diff:1.110")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.15.2")

    // Spring Core and Context for DI
    implementation("org.springframework.boot:spring-boot-starter")

    // Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.2.0.202503040940-r")

    // Json output
    implementation("com.google.code.gson:gson:2.13.0")

    //JavaParser
    implementation("com.github.javaparser:javaparser-core:3.25.4")

    // IO
    implementation("commons-io:commons-io:2.18.0")

    // APTED for Tree Edit Distance
    implementation("eu.mihosoft.ext.apted:apted:0.1")

    // Optional: GumTree via local JAR (disabled dependencies are okay)
    //implementation(files("libs/gumtree.jar"))

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    //Benchmark
    implementation("com.google.guava:guava:32.1.2-jre")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

sourceSets["main"].java {
    srcDir("build/generated-src/antlr/main")
    srcDir("src/main/java")
    exclude("com/example/test/**")

}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}