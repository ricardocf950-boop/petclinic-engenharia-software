# Spring PetClinic — Correção de Bug (AB2)

Este repositório contém o projeto **Spring PetClinic**, com a correção de um bug crítico encontrado no fluxo de **edição de visitas (Visits)**, identificado e resolvido como parte da entrega da AB2.

## 📋 Sobre o projeto

O Spring PetClinic é uma aplicação de demonstração da comunidade Spring, que simula o sistema de uma clínica veterinária: gerenciamento de donos (Owners), pets, tipos de pets, veterinários e visitas (Visits).

## 📌 Issue #2337 — Validação de agendamento futuro de visitas

Conforme especificado na AB1 (RF01, RF02, RF03, RNF01), o sistema deve impedir o registro de visitas com data anterior à data atual, exibindo uma mensagem de erro contextual ao usuário.

Essa regra é garantida pela anotação `@FutureOrPresent` no campo `date` da entidade `Visit`:

```java
@FutureOrPresent(message = "The visit date must be today or a future date.")
private LocalDate date;
```

Por ser validada via Bean Validation (`@Valid`) no lado do servidor, a regra é aplicada de forma consistente tanto na **criação** de uma nova visita quanto na **edição** de uma visita já existente — incluindo o cenário em que o usuário tenta alterar a data de uma visita já registrada para uma data passada.

A mensagem de erro é exibida diretamente abaixo do campo de data, através do fragmento `inputField.html`, sem a necessidade de recarregar a página manualmente (o formulário é reexibido pelo próprio Spring MVC com a mensagem de validação already populada).

## 🐛 Issue #2338 — Edição da descrição de visitas (bug de duplicação)

### Sintoma

Ao usar a funcionalidade de **"edit description"** (editar a descrição de uma visita já existente), o sistema, ao invés de apenas atualizar o registro existente:

- Criava uma **nova visita duplicada** no banco de dados;
- Atualizava a descrição da visita antiga **e** da nova, deixando duas linhas idênticas na lista de visitas do pet.

### Causa raiz

O problema estava no método anotado com `@ModelAttribute("visit")` dentro de `VisitController.java`. Esse tipo de método no Spring MVC é executado **antes de toda requisição** feita para qualquer rota do controller — tanto para as rotas de criação de visita quanto para as de edição.

A implementação original criava uma nova instância de `Visit` e a adicionava à coleção de visitas do `Pet` **sempre que o método era executado**, mesmo durante o fluxo de edição:

```java
@ModelAttribute("visit")
public Visit loadPetWithVisit(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
        Map<String, Object> model) {
    // ...
    Visit visit = new Visit();
    pet.addVisit(visit); // <- Sempre executado, mesmo ao editar uma visita existente
    return visit;
}
```

Como a entidade `Pet` mapeia sua coleção de visitas com `cascade = CascadeType.ALL`, ao salvar o `Owner`/`Pet`, o Hibernate persistia **toda a coleção em memória**, incluindo essa visita "fantasma" criada indevidamente — resultando na duplicação observada.

### Correção aplicada

A lógica de criação de uma nova `Visit` foi isolada para acontecer **somente** no fluxo de criação de uma nova visita (rota `/visits/new`). O carregamento de `Owner` e `Pet`, usado por todas as rotas do controller, foi separado em um método `@ModelAttribute` próprio, que **não cria mais nenhuma visita**:

```java
@ModelAttribute
public void loadOwnerAndPet(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
        Map<String, Object> model) {
    // Carrega apenas owner e pet — não cria Visit nenhuma
}

@GetMapping("/owners/{ownerId}/pets/{petId}/visits/new")
public String initNewVisitForm(@ModelAttribute("pet") Pet pet, Map<String, Object> model) {
    Visit visit = new Visit();
    pet.addVisit(visit); // Só cria a visita nova aqui, quando de fato é uma criação
    model.put("visit", visit);
    return "pets/createOrUpdateVisitForm";
}
```

Os fluxos de edição (`initEditVisitForm` e `processEditVisitForm`) passaram a apenas localizar a visita existente pelo seu ID dentro da coleção do pet, sem criar nenhum registro novo.

### Outras melhorias realizadas durante a correção

- **Criação do `VisitRepository`**, que estava ausente e impedia a compilação de uma versão anterior do controller que dependia dele.
- **Implementação de `equals()` e `hashCode()` em `BaseEntity`**, baseados no `id`, seguindo a boa prática para entidades JPA mapeadas em coleções `Set` — evita inconsistências de identidade em coleções gerenciadas pelo Hibernate.
- **Botão de formulário dinâmico**: o template `createOrUpdateVisitForm.html` agora exibe "Add Visit" ao criar uma nova visita e "Update Visit" ao editar uma existente, usando a expressão Thymeleaf `${visit['new']} ? #{addVisit} : #{updateVisit}`.

## 🖼️ Evidências (antes e depois)

### Antes da correção — duplicação ao editar a descrição

A visita era duplicada toda vez que a descrição era editada, e ambas as linhas recebiam o novo texto.

### Depois da correção — edição funcionando corretamente

Ao editar a descrição de uma visita existente, apenas o registro correspondente é atualizado, sem duplicação. O botão do formulário também passou a refletir corretamente o contexto da operação ("Update Visit").

> As capturas de tela completas do processo de diagnóstico e da correção validada estão na pasta `docs/screenshots` deste repositório.

## 📐 Aderência ao documento da AB1

A implementação desta AB2 buscou seguir fielmente a análise de requisitos e o projeto especificados no relatório da AB1 (Issues #2337 e #2338). A tabela abaixo resume o atendimento a cada requisito:

| Requisito | Status | Observação |
|---|---|---|
| RF01 — Impedir agendamento com data retroativa | ✅ Atendido | Validado via `@FutureOrPresent` |
| RF02 — Exibir mensagem de erro contextual | ✅ Atendido | Exibida no formulário, junto ao campo de data |
| RF03 — Permitir datas presente/futura | ✅ Atendido | Comportamento padrão de `@FutureOrPresent` |
| RF04 — Editar descrição de visita existente | ✅ Atendido | Corrigido o bug de duplicação |
| RF05 — Formulário pré-preenchido na edição | ✅ Atendido | |
| RF06 — Persistir a nova descrição | ✅ Atendido | |
| RF07 — Refletir a atualização na tela de detalhes | ✅ Atendido | |
| RNF01 — Validação obrigatória no backend | ✅ Atendido | Bean Validation, sem dependência de JavaScript |
| RNF02 — Exibição via AJAX, sem reload de página | ⚠️ Não implementado | Optou-se pelo fluxo tradicional de submit + render do Spring MVC, mantendo a simplicidade e a consistência com o restante do projeto (que não utiliza AJAX em nenhum outro formulário) |
| RNF03 — Zero violações em ferramentas de análise estática (SonarQube) | ⚠️ Não verificado | Foi aplicada apenas a formatação padrão do projeto (`spring-javaformat`); não houve execução de análise estática dedicada nesta etapa |
| RNF04 — Latência inferior a 500ms | ✅ Atendido | Operações simples de CRUD local, sem gargalo perceptível |
| RNF05 — Compatibilidade Chrome/Firefox/Edge | ⚠️ Não testado formalmente | Validação realizada apenas no navegador utilizado durante o desenvolvimento |
| Protótipo 3 — Botão "Cancel" no formulário de edição | ⚠️ Não implementado | Optou-se por manter apenas a ação de confirmação ("Update Visit"), por ser suficiente para validar o requisito funcional principal (RF04); a navegação de retorno sem salvar pode ser feita pelo botão "voltar" do navegador |

As divergências marcadas como "não implementado" ou "não verificado" foram decisões conscientes para priorizar o foco desta entrega na correção do bug crítico de duplicação de visitas e na validação funcional das regras de negócio (RFs), deixando os refinamentos de experiência (AJAX, botão de cancelamento) e de qualidade de código (análise estática, testes cross-browser) como possíveis melhorias futuras.

## 🚀 Como executar o projeto

### Pré-requisitos

- Java 17 ou superior
- Maven (o projeto já inclui o Maven Wrapper, não é necessário instalar separadamente)

### Passos

1. Clone o repositório:
   ```bash
   git clone https://github.com/ricardocf950-boop/petclinic-engenharia-software.git
   cd petclinic-engenharia-software
   ```

2. Compile o projeto:
   ```bash
   # Linux/macOS
   ./mvnw clean install -DskipTests

   # Windows
   mvnw.cmd clean install -DskipTests
   ```

3. Execute a aplicação:
   ```bash
   # Linux/macOS
   ./mvnw spring-boot:run

   # Windows
   mvnw.cmd spring-boot:run
   ```

4. Acesse a aplicação no navegador:
   ```
   http://localhost:8080
   ```

> O projeto utiliza o banco de dados **H2 em memória** por padrão — os dados são reiniciados automaticamente a cada execução da aplicação, com base nos dados de exemplo definidos em `src/main/resources/db/h2/data.sql`.

### Testando a correção do bug

1. Acesse um Owner e um dos seus Pets.
2. Clique em **"Add Visit"** e cadastre uma nova visita.
3. Na lista de visitas, clique em **"edit description"**.
4. Altere a descrição e clique em **"Update Visit"**.
5. Confirme que a visita foi atualizada corretamente, sem duplicação.

### Testando a validação de data retroativa

1. Repita os passos acima até abrir o formulário de edição (ou criação) de uma visita.
2. Informe uma data anterior à data atual.
3. Clique em **"Update Visit"** (ou **"Add Visit"**).
4. Confirme que a mensagem *"The visit date must be today or a future date."* é exibida e que a visita não é salva.

## 🛠️ Tecnologias utilizadas

- Java 17
- Spring Boot
- Spring MVC
- Spring Data JPA / Hibernate
- Thymeleaf
- H2 Database
- Maven

## 👤 Autor

Ricardo Costa Filho — Disciplina de Engenharia de Software
