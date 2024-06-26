package com.mgmetehan.accountservice.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgmetehan.accountservice.model.Account;
import com.mgmetehan.accountservice.model.Outbox;
import com.mgmetehan.accountservice.model.enums.OutboxTypes;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OutboxConverter {
    private final ObjectMapper MAPPER = new ObjectMapper();

    public static Outbox convertToOutbox(Account account) {
        try {
            String payload = MAPPER.writeValueAsString(account);
            return Outbox.builder()
                    .type(OutboxTypes.ACCOUNT_CREATED)
                    .payload(payload)
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
