package vallegrande.edu.pe.config;

import vallegrande.edu.pe.repository.ApraqRepository;
import vallegrande.edu.pe.repository.jdbc.JdbcApraqRepository;
import vallegrande.edu.pe.repository.jdbc.JdbcTransactionRepository;
import vallegrande.edu.pe.service.OperationsService;

public final class AppContext {
    private final AppConfig config;
    private final ConnectionFactory connections;
    private final ApraqRepository repository;
    private final OperationsService operations;

    public AppContext() {
        config = AppConfig.load();
        connections = new ConnectionFactory(config);
        repository = new JdbcApraqRepository(connections);
        operations = new OperationsService(connections, new JdbcTransactionRepository());
    }

    public AppConfig config() { return config; }
    public ConnectionFactory connections() { return connections; }
    public ApraqRepository repository() { return repository; }
    public OperationsService operations() { return operations; }
}

