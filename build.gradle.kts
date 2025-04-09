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
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.2.0.202503040940-r")

    implementation("org.slf4j:slf4j-simple:2.0.9")

    implementation("org.antlr:antlr4-runtime:4.13.2") // do parsowania kodu

    antlr("org.antlr:antlr4:4.13.2")

    implementation("commons-io:commons-io:2.18.0")

    //implementation("com.github.gumtreediff:core:4.0.0-beta3")
    //implementation("com.github.gumtreediff:gen.jdt:4.0.0-beta3")
    implementation(files("libs/gumtree.jar"))

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.generateGrammarSource {
    arguments = listOf("-visitor", "-no-listener") // Generowanie klasy z wizytorem
}

sourceSets.main {
    java.srcDirs(
        "src/main/java",
        "build/generated-src/antlr/main") // Dodaj wygenerowany kod do źródeł
}

tasks.test {
    useJUnitPlatform()
}

