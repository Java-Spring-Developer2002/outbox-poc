package com.mgmetehan.accountservice.util;

import com.mgmetehan.accountservice.service.OutboxService;
import io.debezium.config.Configuration;
import io.debezium.embedded.EmbeddedEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static io.debezium.data.Envelope.FieldName.AFTER;
import static io.debezium.data.Envelope.FieldName.OPERATION;

@Slf4j
@Component
public class DebeziumUtil {

    private final Executor executor = Executors.newSingleThreadExecutor(); // Executor kullanarak bir is parcasi olusturuldu.
    private final EmbeddedEngine engine; // Debezium EmbeddedEngine kullanilarak degisikliklerin yakalanmasi ve islenmesi saglandi.
    private final OutboxService outboxService;

    @PostConstruct
    public void start() {
        executor.execute(engine); // Veritabani degisikliklerinin asenkron olarak islenmesi icin Executor kullanildi.
    }

    @PreDestroy
    public void stop() {
        if (engine != null) {
            engine.stop(); // Uygulama sonlandirildiginda Debezium engine'in duzgun bir sekilde kapatilmasi saglandi.
        }
    }

    public DebeziumUtil(Configuration sagaConnector, OutboxService outboxService) {
        this.engine = EmbeddedEngine.create()
                .using(sagaConnector)
                .notifying(this::handleEvent)
                .build();
        this.outboxService = outboxService;
    }

    private void handleEvent(SourceRecord sourceRecord) {
        Struct sourceRecordValue = (Struct) sourceRecord.value();
        var crudOperation = (String) sourceRecordValue.get(OPERATION);
        //r for read //c for create //u for updates //d for delete
        if (sourceRecordValue != null && crudOperation == "c") {
            Struct struct = (Struct) sourceRecordValue.get(AFTER);
            Map<String, Object> payload = struct.schema().fields().stream()
                    .filter(field -> struct.get(field) != null)
                    .collect(Collectors.toMap(Field::name, field -> struct.get(field))); // Veri degisikliginin islenmesi ve uygun bir sekilde kaydedilmesi saglandi.

            outboxService.debeziumDatabaseChange(payload); // Is mantigi islemi icin ilgili servis kullanildi.
        }
    }
}