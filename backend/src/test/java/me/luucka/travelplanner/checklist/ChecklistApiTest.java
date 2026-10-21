package me.luucka.travelplanner.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChecklistApiTest extends IntegrationTest {

    @Autowired JsonMapper json;

    RequestPostProcessor me;
    String trip;

    @BeforeEach
    void fixture() throws Exception {
        me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
        trip = json.readTree(mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"destinations\":[\"Roma\"],"
                                + "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    JsonNode detail() throws Exception {
        return json.readTree(mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    String firstChecklist() throws Exception {
        return detail().get("checklists").get(0).get("id").asString();
    }

    ResultActions addItem(String checklist, String text) throws Exception {
        return mvc.perform(post("/api/checklists/" + checklist + "/items").with(me).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
    }

    String addItemId(String checklist, String text) throws Exception {
        return json.readTree(addItem(checklist, text).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asString();
    }

    @Test
    void newTripHasTwoDefaultChecklists() throws Exception {
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.checklists.length()").value(2))
                .andExpect(jsonPath("$.checklists[0].name").value("Bagaglio"))
                .andExpect(jsonPath("$.checklists[0].position").value(0))
                .andExpect(jsonPath("$.checklists[0].items.length()").value(0))
                .andExpect(jsonPath("$.checklists[1].name").value("Prima di partire"))
                .andExpect(jsonPath("$.checklists[1].position").value(1))
                .andExpect(jsonPath("$.checklists[1].items.length()").value(0));
    }

    @Test
    void addAndToggleItem() throws Exception {
        String list = firstChecklist();
        String item = json.readTree(addItem(list, "Passaporto").andExpect(status().isCreated())
                .andExpect(jsonPath("$.done").value(false)).andReturn().getResponse().getContentAsString())
                .get("id").asString();
        mvc.perform(patch("/api/checklist-items/" + item).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.text").value("Passaporto"));
        assertThat(detail().get("checklists").get(0).get("items").get(0).get("done").asBoolean()).isTrue();
    }

    @Test
    void patchTextOnly() throws Exception {
        String item = addItemId(firstChecklist(), "Passaporto");
        mvc.perform(patch("/api/checklist-items/" + item).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"done\":true}")).andExpect(status().isOk());
        mvc.perform(patch("/api/checklist-items/" + item).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"text\":\"Carta d'identità\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Carta d'identità"))
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void itemsAppendInOrder() throws Exception {
        String list = firstChecklist();
        addItem(list, "uno").andExpect(jsonPath("$.position").value(0));
        addItem(list, "due").andExpect(jsonPath("$.position").value(1));
        addItem(list, "tre").andExpect(jsonPath("$.position").value(2));
        mvc.perform(get("/api/trips/" + trip).with(me))
                .andExpect(jsonPath("$.checklists[0].items[0].text").value("uno"))
                .andExpect(jsonPath("$.checklists[0].items[1].text").value("due"))
                .andExpect(jsonPath("$.checklists[0].items[2].text").value("tre"));
    }

    @Test
    void createRenameAndDeleteChecklist() throws Exception {
        String created = json.readTree(mvc.perform(post("/api/trips/" + trip + "/checklists").with(me).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Extra\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.position").value(2))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/checklists/" + created).with(me).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Altro\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Altro"));
        mvc.perform(delete("/api/checklists/" + created).with(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(detail().get("checklists").size()).isEqualTo(2);
    }

    @Test
    void deleteChecklistDeletesItems() throws Exception {
        String list = firstChecklist();
        String item = addItemId(list, "Passaporto");
        mvc.perform(delete("/api/checklists/" + list).with(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM checklist_item WHERE id = ?::uuid", Integer.class, item))
                .isZero();
        assertThat(detail().get("checklists").size()).isEqualTo(1);
    }

    @Test
    void deleteItem() throws Exception {
        String item = addItemId(firstChecklist(), "Passaporto");
        mvc.perform(delete("/api/checklist-items/" + item).with(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(detail().get("checklists").get(0).get("items").size()).isZero();
    }

    @Test
    void blankItemTextIs400() throws Exception {
        addItem(firstChecklist(), " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").exists());
    }

    @Test
    void blankPatchTextIs400() throws Exception {
        String item = addItemId(firstChecklist(), "Passaporto");
        mvc.perform(patch("/api/checklist-items/" + item).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"text\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.text").exists());
    }
}
