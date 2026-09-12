# Two stages. The first builds the jar the same way the README does, client
# included. The second gets just the jar, so the image that ships carries no
# sources, no Maven and no Node.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# The cache mount keeps the Maven downloads between builds
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -DskipTests package

# The JDK image and not the JRE one: Timefold needs the jdk.random module, and
# the Temurin JRE images leave it out, so the app fails to start on them.
FROM eclipse-temurin:21-jdk
WORKDIR /app
# Runs as a plain user, not root
RUN useradd --system --home /app app
USER app
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
