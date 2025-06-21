**USAGE**

__Not perfect, kind off WIP__

```
<plugin>
  <groupId>com.openapispecs.generator.plugin</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Required: The base package where your controllers are located -->
        <basePackage>com.yourcompany.yourservice.controller</basePackage>

        <!-- Optional: Override default API info -->
        <!-- <apiTitle>My Custom API Title</apiTitle> -->
        <!-- <apiVersion>2.1.0</apiVersion> -->
        <!-- <apiDescription>A more detailed description of my API.</apiDescription> -->

        <!-- Optional: Change the output file name -->
        <!-- <outputFileName>api-spec.yaml</outputFileName> -->

        <!-- Optional: Skip generation with a Maven property -->
        <!-- <skip>${skip.openapi.generation}</skip> -->
    </configuration>
</plugin>

```



**SAMPLE Specs**
```
openapi: "3.0.1"
info:
  title: "spring-boot-3-rest-api-example"
  description: "Spring Boot 3 Rest API example"
  version: "0.0.1-SNAPSHOT"
paths:
  /api/tutorials:
    get:
      summary: "GetAllTutorials"
      operationId: "TutorialController.getAllTutorials"
      parameters:
      - name: "title"
        in: "query"
        required: false
        schema:
          type: "string"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                type: "array"
                items:
                  $ref: "#/components/schemas/Tutorial"
    post:
      summary: "CreateTutorial"
      operationId: "TutorialController.createTutorial"
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/Tutorial"
        required: true
      responses:
        "201":
          description: "Created"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Tutorial"
    delete:
      summary: "DeleteAllTutorials"
      operationId: "TutorialController.deleteAllTutorials"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/HttpStatus"
  /api/tutorials/{id}:
    get:
      summary: "GetTutorialById"
      operationId: "TutorialController.getTutorialById"
      parameters:
      - name: "id"
        in: "path"
        required: true
        schema:
          type: "integer"
          format: "int64"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Tutorial"
    put:
      summary: "UpdateTutorial"
      operationId: "TutorialController.updateTutorial"
      parameters:
      - name: "id"
        in: "path"
        required: true
        schema:
          type: "integer"
          format: "int64"
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/Tutorial"
        required: true
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Tutorial"
    delete:
      summary: "DeleteTutorial"
      operationId: "TutorialController.deleteTutorial"
      parameters:
      - name: "id"
        in: "path"
        required: true
        schema:
          type: "integer"
          format: "int64"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/HttpStatus"
  /api/tutorials/published:
    get:
      summary: "FindByPublished"
      operationId: "TutorialController.findByPublished"
      responses:
        "200":
          description: "OK"
          content:
            application/json:
              schema:
                type: "array"
                items:
                  $ref: "#/components/schemas/Tutorial"
components:
  schemas:
    Tutorial:
      type: "object"
      properties:
        id:
          type: "integer"
          format: "int64"
        title:
          type: "string"
        description:
          type: "string"
        published:
          type: "number"
          format: "double"
    Series:
      type: "object"
      properties:
        value:
          type: "integer"
          format: "int32"
    HttpStatus:
      type: "object"
      properties:
        value:
          type: "integer"
          format: "int32"
        series:
          $ref: "#/components/schemas/Series"
        reasonPhrase:
          type: "string"

```