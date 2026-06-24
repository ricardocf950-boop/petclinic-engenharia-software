/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test class for {@link VisitController}
 *
 * <p>
 * Estes testes cobrem dois comportamentos centrais corrigidos neste ciclo de
 * desenvolvimento:
 * <ul>
 * <li>A edicao da descricao de uma visita ja existente nao deve criar um novo registro
 * (regressao do bug de duplicacao de visitas).</li>
 * <li>O sistema nao deve permitir salvar uma visita (nova ou editada) com data anterior a
 * data atual.</li>
 * </ul>
 *
 * @author Colin But
 * @author Wick Dynex
 */
@WebMvcTest(VisitController.class)
@DisabledInNativeImage
@DisabledInAotMode
class VisitControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 1;

	private static final int TEST_VISIT_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	private Owner george() {
		Owner owner = new Owner();
		owner.setId(TEST_OWNER_ID);
		owner.setFirstName("George");
		owner.setLastName("Franklin");
		owner.setAddress("110 W. Liberty St.");
		owner.setCity("Madison");
		owner.setTelephone("6085551023");

		Pet pet = new Pet();
		pet.setName("Leo");
		pet.setBirthDate(LocalDate.now().minusYears(2));
		owner.addPet(pet);
		pet.setId(TEST_PET_ID);

		Visit existingVisit = new Visit();
		existingVisit.setDate(LocalDate.now());
		existingVisit.setDescription("Checkup");
		pet.addVisit(existingVisit);
		existingVisit.setId(TEST_VISIT_ID);

		return owner;
	}

	@BeforeEach
	void init() {
		given(this.owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(george()));
	}

	@Test
	void initNewVisitForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

	@Test
	void processNewVisitFormSuccess() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", LocalDate.now().toString())
				.param("description", "Annual checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@Test
	void processNewVisitFormWithPastDateHasErrors() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", LocalDate.now().minusDays(1).toString())
				.param("description", "Annual checkup"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("visit", "date"))
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

	@Test
	void initEditVisitFormLoadsExistingVisit() throws Exception {
		mockMvc
			.perform(get("/owners/{ownerId}/pets/{petId}/visits/{visitId}/edit", TEST_OWNER_ID, TEST_PET_ID,
					TEST_VISIT_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

	@Test
	void processEditVisitFormUpdatesDescriptionWithoutDuplicating() throws Exception {
		Owner owner = george();
		given(this.owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(owner));

		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/{visitId}/edit", TEST_OWNER_ID, TEST_PET_ID,
					TEST_VISIT_ID)
				.param("date", LocalDate.now().toString())
				.param("description", "Updated description"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));

		// Garante que a colecao de visitas do pet continua com exatamente 1
		// item apos a edicao, comprovando que nenhum registro novo foi criado
		// (regressao do bug de duplicacao de visitas).
		Pet pet = owner.getPet(TEST_PET_ID);
		assertThat(pet.getVisits()).hasSize(1);
		assertThat(pet.getVisits().iterator().next().getDescription()).isEqualTo("Updated description");
	}

	@Test
	void processEditVisitFormWithPastDateHasErrors() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/{visitId}/edit", TEST_OWNER_ID, TEST_PET_ID,
					TEST_VISIT_ID)
				.param("date", LocalDate.now().minusDays(1).toString())
				.param("description", "Updated description"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("visit", "date"))
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

}