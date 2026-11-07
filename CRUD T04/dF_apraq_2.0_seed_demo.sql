-- Datos de demostración para APRAQ.
-- Requiere que 01-apraq.sql y 02-apraq-ubigeo.sql se hayan ejecutado primero.
-- El script puede ejecutarse más de una vez sin duplicar el conjunto de prueba.
--
-- Cuentas de acceso:
--   admin / admin123
--   sales / sales123
--   warehouse / warehouse123
--   editor / editor123

SET NAMES utf8mb4;
START TRANSACTION;

-- Catálogos principales.
INSERT INTO categories(name, status)
VALUES ('tubers', 'active')
ON DUPLICATE KEY UPDATE status = VALUES(status);
SET @category_id = (SELECT category_id FROM categories WHERE name = 'tubers' LIMIT 1);

INSERT INTO products(name, category_id, status)
VALUES ('yellow_sweet_potato', @category_id, 'active')
ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), status = VALUES(status);
SET @product_id = (SELECT product_id FROM products WHERE name = 'yellow_sweet_potato' LIMIT 1);

INSERT INTO campaigns(name, start_date, end_date, status)
SELECT 'Campaña APRAQ 2026', '2026-01-01', '2026-12-31', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM campaigns
    WHERE name = 'Campaña APRAQ 2026'
      AND start_date = '2026-01-01'
      AND end_date = '2026-12-31'
);
SET @campaign_id = (
    SELECT campaign_id FROM campaigns
    WHERE name = 'Campaña APRAQ 2026'
      AND start_date = '2026-01-01'
      AND end_date = '2026-12-31'
    ORDER BY campaign_id LIMIT 1
);

INSERT INTO members(
    national_id_number, first_name, last_name, phone_number,
    entry_date, address, status
)
VALUES (
    '99999991', 'Rosa', 'Huamán Quispe', '987654300',
    '2026-01-15', 'Sector agrícola de Huamachuco', 'active'
)
ON DUPLICATE KEY UPDATE
    first_name = VALUES(first_name),
    last_name = VALUES(last_name),
    phone_number = VALUES(phone_number),
    entry_date = VALUES(entry_date),
    address = VALUES(address),
    status = VALUES(status);
SET @member_id = (SELECT member_id FROM members WHERE national_id_number = '99999991' LIMIT 1);

INSERT INTO companies(business_name, ruc, status)
VALUES ('Mercado Andino Demo S.A.C.', '20999999991', 'active')
ON DUPLICATE KEY UPDATE business_name = VALUES(business_name), status = VALUES(status);
SET @company_id = (SELECT company_id FROM companies WHERE ruc = '20999999991' LIMIT 1);

-- Personas y contactos para el cliente y para los cuatro roles de la aplicación.
INSERT INTO people(first_name, last_name, document_type, document_number) VALUES
    ('Carla', 'Mendoza Ruiz', 'dni', '99999001'),
    ('Administración', 'APRAQ Demo', 'dni', '99999002'),
    ('Equipo', 'Ventas Demo', 'dni', '99999003'),
    ('Equipo', 'Almacén Demo', 'dni', '99999004'),
    ('Equipo', 'Editorial Demo', 'dni', '99999005')
ON DUPLICATE KEY UPDATE
    first_name = VALUES(first_name),
    last_name = VALUES(last_name);

SET @client_person_id = (SELECT person_id FROM people WHERE document_type = 'dni' AND document_number = '99999001' LIMIT 1);
SET @admin_person_id = (SELECT person_id FROM people WHERE document_type = 'dni' AND document_number = '99999002' LIMIT 1);
SET @sales_person_id = (SELECT person_id FROM people WHERE document_type = 'dni' AND document_number = '99999003' LIMIT 1);
SET @warehouse_person_id = (SELECT person_id FROM people WHERE document_type = 'dni' AND document_number = '99999004' LIMIT 1);
SET @editor_person_id = (SELECT person_id FROM people WHERE document_type = 'dni' AND document_number = '99999005' LIMIT 1);

INSERT INTO contacts(person_id, company_id, phone_number, email, address, location_code)
SELECT @client_person_id, @company_id, '987654301', 'carla.mendoza@example.com',
       'Jr. San Martín 250, Huamachuco', '130901'
WHERE NOT EXISTS (
    SELECT 1 FROM contacts
    WHERE person_id = @client_person_id AND email = 'carla.mendoza@example.com'
);
SET @client_contact_id = (
    SELECT contact_id FROM contacts
    WHERE person_id = @client_person_id AND email = 'carla.mendoza@example.com'
    ORDER BY contact_id LIMIT 1
);

INSERT INTO contacts(person_id, phone_number, email, address, location_code)
SELECT @admin_person_id, '987654302', 'admin@cuentas.apraq.local', 'Sede APRAQ', '130901'
WHERE NOT EXISTS (SELECT 1 FROM contacts WHERE person_id = @admin_person_id AND email = 'admin@cuentas.apraq.local');
SET @admin_contact_id = (SELECT contact_id FROM contacts WHERE person_id = @admin_person_id AND email = 'admin@cuentas.apraq.local' ORDER BY contact_id LIMIT 1);

INSERT INTO contacts(person_id, phone_number, email, address, location_code)
SELECT @sales_person_id, '987654303', 'sales@cuentas.apraq.local', 'Sede APRAQ', '130901'
WHERE NOT EXISTS (SELECT 1 FROM contacts WHERE person_id = @sales_person_id AND email = 'sales@cuentas.apraq.local');
SET @sales_contact_id = (SELECT contact_id FROM contacts WHERE person_id = @sales_person_id AND email = 'sales@cuentas.apraq.local' ORDER BY contact_id LIMIT 1);

INSERT INTO contacts(person_id, phone_number, email, address, location_code)
SELECT @warehouse_person_id, '987654304', 'warehouse@cuentas.apraq.local', 'Sede APRAQ', '130901'
WHERE NOT EXISTS (SELECT 1 FROM contacts WHERE person_id = @warehouse_person_id AND email = 'warehouse@cuentas.apraq.local');
SET @warehouse_contact_id = (SELECT contact_id FROM contacts WHERE person_id = @warehouse_person_id AND email = 'warehouse@cuentas.apraq.local' ORDER BY contact_id LIMIT 1);

INSERT INTO contacts(person_id, phone_number, email, address, location_code)
SELECT @editor_person_id, '987654305', 'editor@cuentas.apraq.local', 'Sede APRAQ', '130901'
WHERE NOT EXISTS (SELECT 1 FROM contacts WHERE person_id = @editor_person_id AND email = 'editor@cuentas.apraq.local');
SET @editor_contact_id = (SELECT contact_id FROM contacts WHERE person_id = @editor_person_id AND email = 'editor@cuentas.apraq.local' ORDER BY contact_id LIMIT 1);

INSERT INTO users(username, password_hash, contact_id, role, status) VALUES
    ('admin', '$2a$12$MXTDIfCdL2GeVv5R8G4aZO4PygE00B1bj5IQmFw3K.G1wiH/974by', @admin_contact_id, 'admin', 'active'),
    ('sales', '$2a$12$zAHNKpoCE6jmPW2BMrVUw.CHUEPBDGRVpzZNj0dmLyiKudIRmwkSK', @sales_contact_id, 'sales', 'active'),
    ('warehouse', '$2a$12$D40Ni60hK7/tuP1nHIqJF.ZGOi/BR852dfBSQnytSKi1lNIlkurRW', @warehouse_contact_id, 'warehouse', 'active'),
    ('editor', '$2a$12$KtlzXVZtE41Hlk5mkHZk6eREp1PWZ2fGecSxIilDxcGFti8yFeAiK', @editor_contact_id, 'editor', 'active')
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    contact_id = VALUES(contact_id),
    role = VALUES(role),
    status = VALUES(status);

SET @admin_user_id = (SELECT user_id FROM users WHERE username = 'admin' LIMIT 1);
SET @sales_user_id = (SELECT user_id FROM users WHERE username = 'sales' LIMIT 1);
SET @warehouse_user_id = (SELECT user_id FROM users WHERE username = 'warehouse' LIMIT 1);
SET @editor_user_id = (SELECT user_id FROM users WHERE username = 'editor' LIMIT 1);

-- Diez solicitudes recibidas desde la web.
INSERT INTO quote_requests(
    submission_key, first_name, last_name, company_name, phone_number,
    email, delivery_address, message, status, submitted_at, assigned_user_id
) VALUES
    ('seed-request-001', 'Ana', 'Torres', 'Bodega Los Andes', '987650001', 'ana.torres@example.com', 'Av. España 101, Trujillo', 'Solicita entrega semanal.', 'quoted', '2026-10-01 14:00:00', @sales_user_id),
    ('seed-request-002', 'Luis', 'Campos', 'Restaurante El Valle', '987650002', 'luis.campos@example.com', 'Jr. Grau 202, Huamachuco', 'Requiere cotización por volumen.', 'reviewing', '2026-10-02 15:00:00', @sales_user_id),
    ('seed-request-003', 'María', 'Paredes', NULL, '987650003', 'maria.paredes@example.com', 'Calle Comercio 303, Otuzco', 'Consulta disponibilidad.', 'new', '2026-10-03 16:00:00', NULL),
    ('seed-request-004', 'José', 'Rojas', 'Distribuidora Norte', '987650004', 'jose.rojas@example.com', 'Av. América 404, Trujillo', 'Entrega durante la mañana.', 'new', '2026-10-04 17:00:00', NULL),
    ('seed-request-005', 'Elena', 'Vargas', NULL, '987650005', 'elena.vargas@example.com', 'Jr. Libertad 505, Santiago de Chuco', 'Solicita presentación en sacos.', 'new', '2026-10-05 18:00:00', NULL),
    ('seed-request-006', 'Carlos', 'Salazar', 'Mercado Central Demo', '987650006', 'carlos.salazar@example.com', 'Calle Lima 606, Chepén', 'Compra para distribución minorista.', 'reviewing', '2026-10-06 14:30:00', @sales_user_id),
    ('seed-request-007', 'Diana', 'Flores', NULL, '987650007', 'diana.flores@example.com', 'Av. Primavera 707, Virú', 'Pregunta por fechas de entrega.', 'new', '2026-10-07 15:30:00', NULL),
    ('seed-request-008', 'Miguel', 'Castillo', 'Comercial Pacífico', '987650008', 'miguel.castillo@example.com', 'Jr. Unión 808, Pacasmayo', 'Necesita precio mayorista.', 'new', '2026-10-08 16:30:00', NULL),
    ('seed-request-009', 'Sofía', 'Reyes', NULL, '987650009', 'sofia.reyes@example.com', 'Calle Real 909, Julcán', 'Solicita información de empaque.', 'new', '2026-10-09 17:30:00', NULL),
    ('seed-request-010', 'Pedro', 'Navarro', 'Alimentos Sierra', '987650010', 'pedro.navarro@example.com', 'Av. Central 1010, Ascope', 'Requiere atención comercial.', 'new', '2026-10-10 18:30:00', NULL)
ON DUPLICATE KEY UPDATE
    first_name = VALUES(first_name),
    last_name = VALUES(last_name),
    company_name = VALUES(company_name),
    phone_number = VALUES(phone_number),
    email = VALUES(email),
    delivery_address = VALUES(delivery_address),
    message = VALUES(message),
    status = VALUES(status),
    submitted_at = VALUES(submitted_at),
    assigned_user_id = VALUES(assigned_user_id);

INSERT INTO quote_request_details(quote_request_id, product_id, quantity_kg, packaging_preference)
SELECT quote_request_id, @product_id,
       100.00 + (quote_request_id % 10) * 25.00,
       'Sacos de 25 kg'
FROM quote_requests
WHERE submission_key BETWEEN 'seed-request-001' AND 'seed-request-010'
ON DUPLICATE KEY UPDATE
    quantity_kg = VALUES(quantity_kg),
    packaging_preference = VALUES(packaging_preference);

SET @request_id = (SELECT quote_request_id FROM quote_requests WHERE submission_key = 'seed-request-001' LIMIT 1);
SET @request_detail_id = (
    SELECT quote_request_detail_id FROM quote_request_details
    WHERE quote_request_id = @request_id AND product_id = @product_id LIMIT 1
);

-- Lote recibido y su movimiento físico de ingreso.
INSERT INTO collection_lots(
    member_id, product_id, campaign_id, entry_date, total_weight_kg, received_by_user_id
)
SELECT @member_id, @product_id, @campaign_id, '2026-09-15', 1000.00, @warehouse_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM collection_lots
    WHERE member_id = @member_id
      AND product_id = @product_id
      AND campaign_id = @campaign_id
      AND entry_date = '2026-09-15'
      AND total_weight_kg = 1000.00
);
SET @lot_id = (
    SELECT collection_lot_id FROM collection_lots
    WHERE member_id = @member_id
      AND product_id = @product_id
      AND campaign_id = @campaign_id
      AND entry_date = '2026-09-15'
      AND total_weight_kg = 1000.00
    ORDER BY collection_lot_id LIMIT 1
);

INSERT INTO inventory_movements(
    collection_lot_id, dispatch_detail_id, operation_key, movement_type,
    quantity_kg, movement_at, reason, recorded_by_user_id
)
VALUES (
    @lot_id, NULL, 'seed-receipt-001', 'receipt',
    1000.00, '2026-09-15 15:00:00', 'Recepción inicial del lote de demostración', @warehouse_user_id
)
ON DUPLICATE KEY UPDATE operation_key = VALUES(operation_key);

-- Cotización aceptada basada en la primera solicitud web.
INSERT INTO quotations(
    quote_request_id, contact_id, company_id, quotation_date, valid_until,
    delivery_address, delivery_location_code, message, status, created_by_user_id
)
SELECT @request_id, @client_contact_id, @company_id, '2026-10-02', '2026-12-31',
       'Jr. San Martín 250, Huamachuco', '130901',
       'Cotización de demostración generada por la carga inicial.', 'accepted', @sales_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM quotations
    WHERE quote_request_id = @request_id
      AND contact_id = @client_contact_id
      AND quotation_date = '2026-10-02'
);
SET @quotation_id = (
    SELECT quotation_id FROM quotations
    WHERE quote_request_id = @request_id
      AND contact_id = @client_contact_id
      AND quotation_date = '2026-10-02'
    ORDER BY quotation_id LIMIT 1
);

INSERT INTO quotation_details(
    quotation_id, quote_request_id, quote_request_detail_id, product_id,
    collection_lot_id, quantity_kg, unit_price, packaging_preference
)
VALUES (
    @quotation_id, @request_id, @request_detail_id, @product_id,
    @lot_id, 100.00, 3.50, 'Sacos de 25 kg'
)
ON DUPLICATE KEY UPDATE
    quantity_kg = VALUES(quantity_kg),
    unit_price = VALUES(unit_price),
    packaging_preference = VALUES(packaging_preference);
SET @quotation_detail_id = (
    SELECT quotation_detail_id FROM quotation_details
    WHERE quotation_id = @quotation_id AND collection_lot_id = @lot_id LIMIT 1
);

-- Venta confirmada, reserva activa y despacho parcial.
INSERT INTO sales(
    company_id, quotation_id, operation_key, sale_date, status, payment_method,
    delivery_address, delivery_location_code, delivery_conditions, user_id
)
VALUES (
    @company_id, @quotation_id, 'seed-sale-001', '2026-10-05',
    'partially_dispatched', 'bank_transfer', 'Jr. San Martín 250, Huamachuco',
    '130901', 'Entrega parcial coordinada con el cliente', @sales_user_id
)
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    payment_method = VALUES(payment_method),
    delivery_address = VALUES(delivery_address),
    delivery_location_code = VALUES(delivery_location_code),
    delivery_conditions = VALUES(delivery_conditions);
SET @sale_id = (SELECT sale_id FROM sales WHERE operation_key = 'seed-sale-001' LIMIT 1);

INSERT INTO sale_details(
    sale_id, quotation_id, quotation_detail_id, collection_lot_id, quantity_kg, unit_price
)
VALUES (@sale_id, @quotation_id, @quotation_detail_id, @lot_id, 100.00, 3.50)
ON DUPLICATE KEY UPDATE quantity_kg = VALUES(quantity_kg), unit_price = VALUES(unit_price);
SET @sale_detail_id = (
    SELECT sale_detail_id FROM sale_details
    WHERE sale_id = @sale_id AND collection_lot_id = @lot_id LIMIT 1
);

INSERT INTO inventory_reservations(
    sale_detail_id, sale_id, collection_lot_id, status, reserved_at, closed_at, created_by_user_id
)
VALUES (
    @sale_detail_id, @sale_id, @lot_id, 'active', '2026-10-05 15:00:00', NULL, @sales_user_id
)
ON DUPLICATE KEY UPDATE
    sale_id = VALUES(sale_id),
    collection_lot_id = VALUES(collection_lot_id),
    status = VALUES(status),
    reserved_at = VALUES(reserved_at),
    closed_at = VALUES(closed_at),
    created_by_user_id = VALUES(created_by_user_id);
SET @reservation_id = (
    SELECT inventory_reservation_id FROM inventory_reservations
    WHERE sale_detail_id = @sale_detail_id LIMIT 1
);

INSERT INTO dispatches(
    sale_id, operation_key, dispatched_at, delivery_reference, notes, registered_by_user_id
)
VALUES (
    @sale_id, 'seed-dispatch-001', '2026-10-08 14:00:00',
    'GUIA-DEMO-001', 'Primer despacho parcial de demostración', @warehouse_user_id
)
ON DUPLICATE KEY UPDATE
    delivery_reference = VALUES(delivery_reference),
    notes = VALUES(notes),
    registered_by_user_id = VALUES(registered_by_user_id);
SET @dispatch_id = (SELECT dispatch_id FROM dispatches WHERE operation_key = 'seed-dispatch-001' LIMIT 1);

INSERT INTO dispatch_details(
    dispatch_id, sale_id, inventory_reservation_id, collection_lot_id, quantity_kg
)
VALUES (@dispatch_id, @sale_id, @reservation_id, @lot_id, 40.00)
ON DUPLICATE KEY UPDATE quantity_kg = VALUES(quantity_kg);
SET @dispatch_detail_id = (
    SELECT dispatch_detail_id FROM dispatch_details
    WHERE dispatch_id = @dispatch_id AND inventory_reservation_id = @reservation_id LIMIT 1
);

INSERT INTO inventory_movements(
    collection_lot_id, dispatch_detail_id, operation_key, movement_type,
    quantity_kg, movement_at, reason, recorded_by_user_id
)
VALUES (
    @lot_id, @dispatch_detail_id, 'seed-dispatch-001:r1', 'dispatch',
    40.00, '2026-10-08 14:00:00', 'Despacho parcial de demostración', @warehouse_user_id
)
ON DUPLICATE KEY UPDATE
    dispatch_detail_id = VALUES(dispatch_detail_id),
    quantity_kg = VALUES(quantity_kg),
    movement_at = VALUES(movement_at),
    reason = VALUES(reason),
    recorded_by_user_id = VALUES(recorded_by_user_id);

-- Contenido editorial de ejemplo.
INSERT INTO news(
    title, description, photos, status, published_at, author_user_id
)
SELECT 'Bienvenida a la campaña APRAQ 2026',
       'APRAQ inicia una nueva campaña de acopio y comercialización con sus productores asociados.',
       JSON_ARRAY('/images/banner-inicio-1.png'), 'published', '2026-10-01 12:00:00', @editor_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM news
    WHERE title = 'Bienvenida a la campaña APRAQ 2026'
      AND author_user_id = @editor_user_id
);

COMMIT;

-- Control rápido esperado después de una instalación limpia:
-- 21 tablas con al menos un registro y 10 solicitudes web con 10 detalles.
