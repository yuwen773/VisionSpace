package com.yuwen.visionspace.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionTypeTest {

    @Test
    void acceptsEnumNameAndDocumentedLowercaseValue() {
        assertEquals(ActionType.REGENERATE, ActionType.fromJson("REGENERATE"));
        assertEquals(ActionType.REGENERATE, ActionType.fromJson("regenerate"));
        assertEquals(ActionType.RESEARCH, ActionType.fromJson("Research"));
        assertEquals(ActionType.RETURN, ActionType.fromJson("return"));
    }

    @Test
    void emptyValueMeansNoAction() {
        assertNull(ActionType.fromJson(null));
        assertNull(ActionType.fromJson(""));
    }

    @Test
    void unknownValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ActionType.fromJson("delete-everything"));
    }
}
