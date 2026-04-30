plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.financial"
version = "1.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

val langchain4jVersion = "0.36.0"
val milvusVersion = "2.4.3"

dependencies {
    // LangChain4j core
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-milvus:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-spring-boot-starter:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion")

    // Milvus Java SDK
    implementation("io.milvus:milvus-sdk-java:$milvusVersion")

    // Document parsing
    implementation("org.apache.pdfbox:pdfbox:3.0.2")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // BM25 keyword search via Lucene
    implementation("org.apache.lucene:lucene-core:9.10.0")
    implementation("org.apache.lucene:lucene-analysis-common:9.10.0")
    implementation("org.apache.lucene:lucene-queryparser:9.10.0")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Redis cache
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // HTTP client for reranker API
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database (H2 for dev, PostgreSQL for prod)
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito:mockito-core")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("financial-rag.jar")
}
