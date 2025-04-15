plugins {
    id("java")
    id("antlr")
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
    // Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.2.0.202503040940-r")

    // Logging
    //implementation("org.slf4j:slf4j-simple:2.0.9")

    // ANTLR parser
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")

    // IO
    implementation("commons-io:commons-io:2.18.0")

    // APTED for Tree Edit Distance
    implementation("eu.mihosoft.ext.apted:apted:0.1")

    // Optional: GumTree via local JAR (disabled dependencies are okay)
    implementation(files("libs/gumtree.jar"))

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.generateGrammarSource {
    arguments = listOf("-visitor")
    outputDirectory = file("build/generated-src/antlr/main")
}

sourceSets["main"].java {
    srcDir("build/generated-src/antlr/main")
    srcDir("src/main/java")
}

tasks.test {
    useJUnitPlatform()
}
