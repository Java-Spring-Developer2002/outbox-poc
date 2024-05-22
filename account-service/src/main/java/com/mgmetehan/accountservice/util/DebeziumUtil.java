package com.mgmetehan.accountservice.util;

import com.mgmetehan.accountservice.service.OutboxService;
import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static io.debezium.data.Envelope.FieldName.AFTER;
import static io.debezium.data.Envelope.FieldName.OPERATION;

@Slf4j
@Component
public class DebeziumUtil {
// Bu sinif, Debezium adli bir degisiklik veri yakalama aracinin kullanimini icerir.

    //Bu Executor, arka planda calisan is parcaciklarini yonetmeye ve calistirmaya olanak tanir.
    private final Executor executor = Executors.newSingleThreadExecutor(); // Executor kullanarak bir is parcasi olusturuldu.
    private final DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine; // Debezium EmbeddedEngine kullanilarak degisikliklerin yakalanmasi ve islenmesi saglandi.
    private final OutboxService outboxService;

    @PostConstruct
    public void start() {
        executor.execute(debeziumEngine); // Veritabani degisikliklerinin asenkron olarak islenmesi icin Executor kullanildi.
    }

    @PreDestroy
    public void stop() throws IOException {
        if (debeziumEngine != null)
            debeziumEngine.close(); // Uygulama sonlandirildiginda Debezium engine'in duzgun bir sekilde kapatilmasi saglandi.
    }

    public DebeziumUtil(Configuration configuration, OutboxService outboxService) {
        this.debeziumEngine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(configuration.asProperties())
                .notifying(this::handleEvent)
                .build();
        this.outboxService = outboxService;
    }

    // SourceRecord, Debezium gibi bazi veri degisiklikleri izleme ve yakalama araclarinda kullanilan bir siniftir.
    private void handleEvent(RecordChangeEvent<SourceRecord> sourceRecordRecordChangeEvent) {
        SourceRecord sourceRecord = sourceRecordRecordChangeEvent.record();
        // Degisiklik verisi islenir ve uygun bir sekilde kaydedilir.
        Struct sourceRecordValue = (Struct) sourceRecord.value();// Kaynagin (sourceRecord) degerini alir.

        // Degisiklik turunu (create, update, delete vb.) belirlemek icin kaynaktan CRUD operasyonunu al
        var crudOperation = (String) sourceRecordValue.get(OPERATION);

        //r for read //c for create //u for updates //d for delete
        // CRUD islemleri kontrol edilir ve degisiklikler uygun sekilde islenir.
        if (Objects.equals(crudOperation, "c") || Objects.equals(crudOperation, "u")) {
            Struct struct = (Struct) sourceRecordValue.get(AFTER);
            Map<String, Object> payload = struct.schema().fields().stream()
                    .filter(field -> struct.get(field) != null)
                    .collect(Collectors.toMap(Field::name, struct::get)); // Veri degisikliginin islenmesi ve uygun bir sekilde kaydedilmesi saglandi.

            outboxService.debeziumDatabaseChange(payload); // Is mantigi islemi icin ilgili servis kullanildi.
        }
    }
}