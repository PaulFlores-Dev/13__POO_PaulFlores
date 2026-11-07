-- APRAQ | INSTALACIÓN COMPLETA REVISADA
-- Compatible con MySQL 8.0.16 o posterior; destinado a una BASE NUEVA.
-- Ejecutar completo con mysql en modo por lotes, SIN --force.
-- Ejemplo en la EC2 de base de datos:
-- sudo mysql < /home/ubuntu/apraq_instalacion_completa_revisada.sql
-- Si apraq ya existe, CREATE DATABASE detendrá la importación deliberadamente.
-- No es una migración ni un script para reiniciar una instalación existente.
-- No elimina bases, tablas ni registros. No crea usuarios MySQL ni certificados.
-- Ante un fallo, detenerse: las instrucciones DDL no se revierten como un bloque.
-- Se conservan los identificadores y estados en inglés por compatibilidad.
-- Los comentarios y el catálogo inicial están en español.
-- Cada conexión de Flask/JavaFX debe establecer UTC; este SET solo afecta esta sesión.
-- La aplicación debe controlar roles, transacciones, saldo de inventario,
-- fechas de campaña y límites del formulario; los CHECK no sustituyen esas reglas.
-- El catálogo no incluye usuarios de aplicación, ubigeos, productores ni lotes.
-- 1. BASE DE DATOS Y CONFIGURACIÓN DE SESIÓN
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE DATABASE apraq CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE apraq;

-- Modelo original: Redgate Data Modeler (https://datamodeler.redgate-platform.com)
-- Fecha de modificación del modelo original: 2026-10-18 18:02:11.05

-- 2. TABLAS E ÍNDICES
-- Tabla: campaigns
CREATE TABLE campaigns (
    campaign_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    name varchar(80) NOT NULL COMMENT 'Nombre de campaña; puede repetirse en períodos diferentes.',
    start_date date NOT NULL COMMENT 'Fecha inicial de la campaña.',
    end_date date NOT NULL COMMENT 'Fecha final de la campaña, inclusive.',
    status varchar(12) NOT NULL DEFAULT 'planned' COMMENT 'Estado de campaña: planned, active, closed o cancelled.',
    CHECK (( CHAR_LENGTH( TRIM( name ) ) > 0 )),
    CHECK (( end_date >= start_date )),
    CHECK (( status in ( 'planned' , 'active' , 'closed' , 'cancelled' ) )),
    CONSTRAINT pk_campaigns PRIMARY KEY (campaign_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Campañas agrícolas que agrupan lotes de acopio dentro de un período.';

CREATE INDEX ix_campaigns_status_dates ON campaigns (status,start_date,end_date);

-- Tabla: categories
CREATE TABLE categories (
    category_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    name varchar(80) NOT NULL COMMENT 'Nombre único de categoría; el catálogo inicial utiliza nombres en español.',
    status varchar(10) NOT NULL DEFAULT 'active' COMMENT 'Disponibilidad del catálogo: active o inactive.',
    UNIQUE INDEX uq_categories_name (name),
    CHECK (( CHAR_LENGTH( TRIM( name ) ) > 0 )),
    CHECK (( status in ( 'active' , 'inactive' ) )),
    CONSTRAINT pk_categories PRIMARY KEY (category_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Catálogo de clasificación de productos; las categorías se comparten entre solicitudes.';

-- Tabla: collection_lots
CREATE TABLE collection_lots (
    collection_lot_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    member_id bigint UNSIGNED NOT NULL COMMENT 'Productor que entrega el lote.',
    product_id bigint UNSIGNED NOT NULL COMMENT 'Único producto contenido en el lote.',
    campaign_id bigint UNSIGNED NOT NULL COMMENT 'Campaña del lote; la aplicación debe validar entry_date contra sus fechas.',
    entry_date date NOT NULL COMMENT 'Fecha de recepción física del lote.',
    total_weight_kg decimal(12,2) NOT NULL COMMENT 'Kilogramos originales recibidos; crear exactamente un movimiento de ingreso correspondiente en la misma transacción.',
    received_by_user_id bigint UNSIGNED NOT NULL COMMENT 'Cuenta autorizada que registró la recepción.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    UNIQUE INDEX uq_collection_lots_product_anchor (collection_lot_id,product_id),
    CHECK (( total_weight_kg > 0 )),
    CONSTRAINT pk_collection_lots PRIMARY KEY (collection_lot_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Entregas trazables de productores. El peso original es histórico y no se suma nuevamente al saldo de movimientos.';

CREATE INDEX ix_collection_lots_campaign_date ON collection_lots (campaign_id,entry_date);

-- Tabla: companies
CREATE TABLE companies (
    company_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    business_name varchar(150) NOT NULL COMMENT 'Razón social registrada y verificada por el personal.',
    ruc char(11) NOT NULL COMMENT 'RUC peruano único de once dígitos; validar el formato no confirma su inscripción en SUNAT.',
    status varchar(10) NOT NULL DEFAULT 'active' COMMENT 'Disponibilidad del cliente: active o inactive.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    UNIQUE INDEX uq_companies_ruc (ruc),
    CHECK (( CHAR_LENGTH( TRIM( business_name ) ) > 0 )),
    CHECK (( REGEXP_LIKE( ruc , '^[0-9]{11}$' ) )),
    CHECK (( status in ( 'active' , 'inactive' ) )),
    CONSTRAINT pk_companies PRIMARY KEY (company_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Clientes empresariales registrados o seleccionados por personal autorizado antes de una venta B2B.';

-- Tabla: contacts
CREATE TABLE contacts (
    contact_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    person_id bigint UNSIGNED NOT NULL COMMENT 'Persona titular de estos medios de contacto.',
    company_id bigint UNSIGNED NULL COMMENT 'Empresa opcional representada; una persona puede tener contactos separados para distintas empresas.',
    phone_number varchar(20) NULL COMMENT 'Teléfono; debe existir al menos un teléfono o un correo.',
    email varchar(254) NULL COMMENT 'Correo de contacto; opcional cuando existe teléfono.',
    address varchar(255) NULL COMMENT 'Dirección completa opcional, de hasta 255 caracteres.',
    location_code char(6) NULL COMMENT 'Código de distrito opcional de la dirección del contacto.',
    UNIQUE INDEX uq_contacts_company_anchor (contact_id,company_id),
    CHECK (( phone_number is not null or email is not null )),
    CHECK (( phone_number is null or ( CHAR_LENGTH( TRIM( phone_number ) ) between 6 and 20 and REGEXP_LIKE( phone_number , '^[+0-9 ()-]+$' ) ) )),
    CHECK (( email is null or REGEXP_LIKE( email , '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$' ) )),
    CHECK (( address is null or CHAR_LENGTH( TRIM( address ) ) > 0 )),
    CONSTRAINT pk_contacts PRIMARY KEY (contact_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Medios de contacto de una persona, vinculados opcionalmente con una empresa y una ubicación.';

CREATE INDEX ix_contacts_email ON contacts (email);

-- Tabla: dispatch_details
CREATE TABLE dispatch_details (
    dispatch_detail_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    dispatch_id bigint UNSIGNED NOT NULL COMMENT 'Cabecera del despacho registrado.',
    sale_id bigint UNSIGNED NOT NULL COMMENT 'Referencia de venta que impide despachar una reserva de otra venta.',
    inventory_reservation_id bigint UNSIGNED NOT NULL COMMENT 'Reserva atendida mediante esta línea de despacho.',
    collection_lot_id bigint UNSIGNED NOT NULL COMMENT 'Referencia de lote que relaciona despacho, reserva y movimiento de inventario.',
    quantity_kg decimal(12,2) NOT NULL COMMENT 'Kilogramos positivos entregados; la aplicación debe validar el saldo reservado mediante bloqueo de fila.',
    UNIQUE INDEX uq_dispatch_details_dispatch_reservation (dispatch_id,inventory_reservation_id),
    UNIQUE INDEX uq_dispatch_details_movement_anchor (dispatch_detail_id,collection_lot_id),
    CHECK (( quantity_kg > 0 )),
    CONSTRAINT pk_dispatch_details PRIMARY KEY (dispatch_detail_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Líneas de entrega parcial o total de la misma venta y lote; cada línea requiere un movimiento de inventario correspondiente.';

-- Tabla: dispatches
CREATE TABLE dispatches (
    dispatch_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    sale_id bigint UNSIGNED NOT NULL COMMENT 'Venta despachada; las entregas parciales generan cabeceras adicionales.',
    operation_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Clave estable generada por el cliente; reutilizarla al reintentar el mismo despacho.',
    dispatched_at datetime(6) NOT NULL COMMENT 'Fecha y hora UTC de la entrega física.',
    delivery_reference varchar(80) NULL COMMENT 'Referencia documental opcional del despacho; no es un comprobante tributario electrónico.',
    notes varchar(255) NULL COMMENT 'Observaciones opcionales del despacho.',
    registered_by_user_id bigint UNSIGNED NOT NULL COMMENT 'Cuenta de almacén o administración que registra el despacho.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    UNIQUE INDEX uq_dispatches_operation_key (operation_key),
    UNIQUE INDEX uq_dispatches_sale_anchor (dispatch_id,sale_id),
    CHECK (( CHAR_LENGTH( TRIM( operation_key ) ) > 0 )),
    CHECK (( delivery_reference is null or CHAR_LENGTH( TRIM( delivery_reference ) ) > 0 )),
    CONSTRAINT pk_dispatches PRIMARY KEY (dispatch_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Cabeceras de entregas físicas. Admiten varios despachos por venta; persistir únicamente operaciones completadas de forma atómica.';

CREATE INDEX ix_dispatches_sale_date ON dispatches (sale_id,dispatched_at);

-- Tabla: geographic_locations
CREATE TABLE geographic_locations (
    location_code char(6) NOT NULL COMMENT 'Clave primaria: códigos de departamento, provincia y distrito concatenados en seis dígitos.',
    department_code char(2) NOT NULL COMMENT 'Código de departamento de dos dígitos; conservar los ceros iniciales.',
    department_name varchar(100) NOT NULL COMMENT 'Nombre del departamento.',
    province_code char(2) NOT NULL COMMENT 'Código de provincia de dos dígitos dentro del departamento.',
    province_name varchar(100) NOT NULL COMMENT 'Nombre de la provincia.',
    district_code char(2) NOT NULL COMMENT 'Código de distrito de dos dígitos dentro de la provincia.',
    district_name varchar(100) NOT NULL COMMENT 'Nombre del distrito.',
    CHECK (( REGEXP_LIKE( location_code , '^[0-9]{6}$' ) and REGEXP_LIKE( department_code , '^[0-9]{2}$' ) and REGEXP_LIKE( province_code , '^[0-9]{2}$' ) and REGEXP_LIKE( district_code , '^[0-9]{2}$' ) and location_code = CONCAT( department_code , province_code , district_code ) )),
    CHECK (( CHAR_LENGTH( TRIM( department_name ) ) > 0 and CHAR_LENGTH( TRIM( province_name ) ) > 0 and CHAR_LENGTH( TRIM( district_name ) ) > 0 )),
    CONSTRAINT pk_geographic_locations PRIMARY KEY (location_code)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Códigos geográficos oficiales del Perú utilizados en direcciones de contacto y entrega.';

CREATE INDEX ix_geographic_locations_region ON geographic_locations (department_code,province_code);

-- Tabla: inventory_movements
CREATE TABLE inventory_movements (
    inventory_movement_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    collection_lot_id bigint UNSIGNED NOT NULL COMMENT 'Lote cuyo inventario físico cambia.',
    dispatch_detail_id bigint UNSIGNED NULL COMMENT 'Obligatorio solo para movimientos de despacho; único para impedir un segundo descuento de la misma línea.',
    operation_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Clave estable para reintentos del movimiento, distinta de las claves de cabecera.',
    movement_type varchar(20) NOT NULL COMMENT 'Tipo de movimiento: receipt, dispatch, adjustment_in o adjustment_out.',
    quantity_kg decimal(12,2) NOT NULL COMMENT 'Cantidad estrictamente positiva en kilogramos; movement_type determina si suma o resta.',
    movement_at datetime(6) NOT NULL COMMENT 'Fecha y hora UTC del cambio físico de inventario.',
    reason varchar(255) NOT NULL COMMENT 'Motivo obligatorio; los ajustes deben explicar la corrección.',
    recorded_by_user_id bigint UNSIGNED NOT NULL COMMENT 'Cuenta de almacén o administración que registra el movimiento.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    UNIQUE INDEX uq_inventory_movements_operation_key (operation_key),
    UNIQUE INDEX uq_inventory_movements_dispatch_detail (dispatch_detail_id),
    CHECK (( CHAR_LENGTH( TRIM( operation_key ) ) > 0 )),
    CHECK (( movement_type in ( 'receipt' , 'dispatch' , 'adjustment_in' , 'adjustment_out' ) )),
    CHECK (( quantity_kg > 0 )),
    CHECK (( CHAR_LENGTH( TRIM( reason ) ) > 0 )),
    CHECK (( ( movement_type = 'dispatch' and dispatch_detail_id is not null ) or ( movement_type <> 'dispatch' and dispatch_detail_id is null ) )),
    CONSTRAINT pk_inventory_movements PRIMARY KEY (inventory_movement_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Historial de inventario para agregar movimientos, sin reescribir los anteriores. Ingresos y ajustes positivos suman; despachos y ajustes negativos restan.';

CREATE INDEX ix_inventory_movements_lot_time ON inventory_movements (collection_lot_id,movement_at);

-- Tabla: inventory_reservations
CREATE TABLE inventory_reservations (
    inventory_reservation_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    sale_detail_id bigint UNSIGNED NOT NULL COMMENT 'Línea de venta única cuya cantidad se reserva; un solo ciclo de reserva por línea.',
    sale_id bigint UNSIGNED NOT NULL COMMENT 'Referencia de venta que garantiza correspondencia con la línea reservada.',
    collection_lot_id bigint UNSIGNED NOT NULL COMMENT 'Referencia de lote que garantiza correspondencia con la línea de venta.',
    status varchar(10) NOT NULL DEFAULT 'active' COMMENT 'Estado de reserva: active, fulfilled o released.',
    reserved_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora UTC en que se comprometió el inventario.',
    closed_at datetime(6) NULL COMMENT 'Fecha y hora UTC de cierre; obligatoria en estados fulfilled o released.',
    created_by_user_id bigint UNSIGNED NOT NULL COMMENT 'Cuenta de ventas o administración que crea la reserva.',
    UNIQUE INDEX uq_inventory_reservations_sale_detail (sale_detail_id),
    UNIQUE INDEX uq_inventory_reservations_dispatch_anchor (inventory_reservation_id,sale_id,collection_lot_id),
    CHECK (( ( status = 'active' and closed_at is null ) or ( status in ( 'fulfilled' , 'released' ) and closed_at is not null and closed_at >= reserved_at ) )),
    CONSTRAINT pk_inventory_reservations PRIMARY KEY (inventory_reservation_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Reservas completas por línea, creadas atómicamente al confirmar la venta; el saldo reservado se calcula, no se duplica.';

CREATE INDEX ix_inventory_reservations_lot_status ON inventory_reservations (collection_lot_id,status);

-- Tabla: members
CREATE TABLE members (
    member_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    national_id_number char(8) NOT NULL COMMENT 'DNI único del productor de ocho dígitos; conservar los ceros iniciales.',
    first_name varchar(80) NOT NULL COMMENT 'Nombres del productor.',
    last_name varchar(80) NOT NULL COMMENT 'Apellidos del productor.',
    phone_number varchar(20) NULL COMMENT 'Teléfono opcional del productor.',
    entry_date date NOT NULL COMMENT 'Fecha de incorporación del productor a APRAQ.',
    address varchar(255) NULL COMMENT 'Dirección opcional del productor.',
    status varchar(10) NOT NULL DEFAULT 'active' COMMENT 'Estado del asociado: active o inactive.',
    UNIQUE INDEX uq_members_dni (national_id_number),
    CHECK (( REGEXP_LIKE( national_id_number , '^[0-9]{8}$' ) )),
    CHECK (( CHAR_LENGTH( TRIM( first_name ) ) > 0 and CHAR_LENGTH( TRIM( last_name ) ) > 0 )),
    CHECK (( phone_number is null or ( CHAR_LENGTH( TRIM( phone_number ) ) between 6 and 20 and REGEXP_LIKE( phone_number , '^[+0-9 ()-]+$' ) ) )),
    CHECK (( address is null or CHAR_LENGTH( TRIM( address ) ) > 0 )),
    CHECK (( status in ( 'active' , 'inactive' ) )),
    CONSTRAINT pk_members PRIMARY KEY (member_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Productores agrícolas que entregan lotes a APRAQ; independientes de las cuentas de contactos comerciales.';

-- Tabla: news
CREATE TABLE news (
    news_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    title varchar(200) NOT NULL COMMENT 'Título de la publicación.',
    description text NOT NULL COMMENT 'Contenido de la publicación.',
    photos json NULL COMMENT 'Arreglo JSON opcional con rutas o direcciones URL de imágenes.',
    status varchar(10) NOT NULL DEFAULT 'draft' COMMENT 'Estado de publicación: draft, published o archived.',
    published_at datetime(6) NULL COMMENT 'Fecha y hora UTC de publicación; obligatoria en contenido publicado o archivado.',
    author_user_id bigint UNSIGNED NOT NULL COMMENT 'Cuenta de edición o administración responsable de la publicación.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    CHECK (( CHAR_LENGTH( TRIM( title ) ) > 0 and CHAR_LENGTH( TRIM( description ) ) > 0 )),
    CHECK (( photos is null or JSON_TYPE( photos ) = 'ARRAY' )),
    CHECK (( ( status = 'draft' and published_at is null ) or ( status in ( 'published' , 'archived' ) and published_at is not null ) )),
    CONSTRAINT pk_news PRIMARY KEY (news_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Noticias web gestionadas por edición o administración; independientes de ventas e inventario.';

CREATE INDEX ix_news_status_date ON news (status,published_at);

-- Tabla: people
CREATE TABLE people (
    person_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    first_name varchar(100) NOT NULL COMMENT 'Nombres de la persona.',
    last_name varchar(100) NOT NULL COMMENT 'Apellidos de la persona.',
    document_type varchar(3) NULL COMMENT 'Tipo de documento opcional: dni o ce; se proporciona junto con document_number.',
    document_number varchar(11) NULL COMMENT 'Documento opcional: DNI de ocho dígitos o CE de uno a once caracteres alfanuméricos.',
    UNIQUE INDEX uq_people_document (document_type,document_number),
    CHECK (( CHAR_LENGTH( TRIM( first_name ) ) > 0 and CHAR_LENGTH( TRIM( last_name ) ) > 0 )),
    CHECK (( ( document_type is null and document_number is null ) or ( document_type is not null and document_number is not null and ( ( document_type = 'dni' and REGEXP_LIKE( document_number , '^[0-9]{8}$' ) ) or ( document_type = 'ce' and REGEXP_LIKE( document_number , '^[a-zA-Z0-9]{1,11}$' ) ) ) ) )),
    CONSTRAINT pk_people PRIMARY KEY (person_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Personas naturales usadas como contactos y titulares de cuentas; el documento es opcional para contactos comerciales.';

-- Tabla: products
CREATE TABLE products (
    product_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    name varchar(80) NOT NULL COMMENT 'Nombre comercial único del producto; el catálogo inicial utiliza nombres en español.',
    category_id bigint UNSIGNED NOT NULL COMMENT 'Categoría que clasifica el producto.',
    status varchar(10) NOT NULL DEFAULT 'active' COMMENT 'Disponibilidad para nuevas operaciones: active o inactive.',
    UNIQUE INDEX uq_products_name (name),
    CHECK (( CHAR_LENGTH( TRIM( name ) ) > 0 )),
    CHECK (( status in ( 'active' , 'inactive' ) )),
    CONSTRAINT pk_products PRIMARY KEY (product_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Catálogo agrícola compartido: Papa Única, Camote Amarillo y Maracuyá.';

-- Tabla: quotation_details
CREATE TABLE quotation_details (
    quotation_detail_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    quotation_id bigint UNSIGNED NOT NULL COMMENT 'Cotización que contiene la línea del lote ofrecido.',
    quote_request_id bigint UNSIGNED NULL COMMENT 'Referencia opcional de la solicitud de origen para validar la coherencia con su detalle.',
    quote_request_detail_id bigint UNSIGNED NULL COMMENT 'Línea opcional del producto solicitado; se proporciona junto con quote_request_id.',
    product_id bigint UNSIGNED NOT NULL COMMENT 'Referencia que garantiza que el lote ofrecido corresponda al producto solicitado cuando existe una línea de origen.',
    collection_lot_id bigint UNSIGNED NOT NULL COMMENT 'Lote de acopio ofrecido al cliente.',
    quantity_kg decimal(12,2) NOT NULL COMMENT 'Kilogramos positivos ofrecidos de este lote.',
    unit_price decimal(12,2) NOT NULL COMMENT 'Precio positivo ofrecido en soles por kilogramo.',
    packaging_preference varchar(100) NULL COMMENT 'Presentación opcional ofrecida para esta línea.',
    UNIQUE INDEX uq_quotation_details_quotation_lot (quotation_id,collection_lot_id),
    UNIQUE INDEX uq_quotation_details_sale_anchor (quotation_detail_id,quotation_id,collection_lot_id),
    CHECK (( ( quote_request_id is null and quote_request_detail_id is null ) or ( quote_request_id is not null and quote_request_detail_id is not null ) )),
    CHECK (( quantity_kg > 0 )),
    CHECK (( unit_price > 0 )),
    CHECK (( packaging_preference is null or CHAR_LENGTH( TRIM( packaging_preference ) ) > 0 )),
    CONSTRAINT pk_quotation_details PRIMARY KEY (quotation_detail_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Líneas de oferta asignadas a lotes. Cotizar por sí solo no reserva ni despacha inventario.';

-- Tabla: quotations
CREATE TABLE quotations (
    quotation_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    quote_request_id bigint UNSIGNED NULL COMMENT 'Solicitud web de origen opcional; NULL indica una cotización iniciada manualmente.',
    contact_id bigint UNSIGNED NOT NULL COMMENT 'Contacto destinatario de la cotización.',
    company_id bigint UNSIGNED NULL COMMENT 'Cliente empresarial opcional; debe completarse antes de convertir la cotización en venta B2B.',
    quotation_date date NOT NULL COMMENT 'Fecha de la oferta.',
    valid_until date NOT NULL COMMENT 'Última fecha de vigencia de la oferta.',
    delivery_address varchar(255) NOT NULL COMMENT 'Dirección de entrega verificada o completada por el personal.',
    delivery_location_code char(6) NOT NULL COMMENT 'Código de distrito del destino, completado por el personal.',
    message text NULL COMMENT 'Observaciones comerciales opcionales.',
    status varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'Estado de oferta: pending, in_negotiation, accepted, rejected, expired o cancelled.',
    created_by_user_id bigint UNSIGNED NOT NULL COMMENT 'Cuenta de ventas o administración que crea la cotización.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    UNIQUE INDEX uq_quotations_company_anchor (quotation_id,company_id),
    UNIQUE INDEX uq_quotations_request_anchor (quotation_id,quote_request_id),
    CHECK (( valid_until >= quotation_date )),
    CHECK (( CHAR_LENGTH( TRIM( delivery_address ) ) > 0 )),
    CHECK (( status in ( 'pending' , 'in_negotiation' , 'accepted' , 'rejected' , 'expired' , 'cancelled' ) )),
    CONSTRAINT pk_quotations PRIMARY KEY (quotation_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Cabecera de oferta comercial registrada por el personal; puede originarse en una solicitud web o crearse manualmente.';

CREATE INDEX ix_quotations_status_validity ON quotations (status,valid_until);

-- Tabla: quote_request_details
CREATE TABLE quote_request_details (
    quote_request_detail_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    quote_request_id bigint UNSIGNED NOT NULL COMMENT 'Solicitud web que contiene esta línea de producto.',
    product_id bigint UNSIGNED NOT NULL COMMENT 'Producto del catálogo solicitado.',
    quantity_kg decimal(12,2) NOT NULL COMMENT 'Cantidad solicitada en kilogramos; la aplicación convierte las toneladas antes de guardar.',
    packaging_preference varchar(100) NULL COMMENT 'Preferencia opcional de presentación del producto.',
    UNIQUE INDEX uq_quote_request_details_request_product (quote_request_id,product_id),
    UNIQUE INDEX uq_quote_request_details_source_anchor (quote_request_detail_id,quote_request_id,product_id),
    CHECK (( quantity_kg > 0 )),
    CHECK (( packaging_preference is null or CHAR_LENGTH( TRIM( packaging_preference ) ) > 0 )),
    CONSTRAINT pk_quote_request_details PRIMARY KEY (quote_request_detail_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Productos de una solicitud web; permite varios productos sin repetir la cabecera.';

-- Tabla: quote_requests
CREATE TABLE quote_requests (
    quote_request_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    submission_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Clave de reintento generada por el cliente para todo el formulario; reutilizarla al reintentar.',
    first_name varchar(100) NOT NULL COMMENT 'Nombres enviados mediante el formulario público.',
    last_name varchar(100) NOT NULL COMMENT 'Apellidos enviados mediante el formulario público.',
    company_name varchar(150) NULL COMMENT 'Nombre opcional de empresa sin verificar, enviado por el visitante; no es una clave foránea de companies.',
    phone_number varchar(20) NOT NULL COMMENT 'Teléfono del visitante para responder a la solicitud.',
    email varchar(254) NOT NULL COMMENT 'Correo del visitante para responder a la solicitud.',
    delivery_address varchar(255) NOT NULL COMMENT 'Destino de entrega solicitado; el personal puede completar el distrito posteriormente.',
    message text NULL COMMENT 'Mensaje opcional del visitante.',
    status varchar(12) NOT NULL DEFAULT 'new' COMMENT 'Estado de atención: new, reviewing, quoted, closed o cancelled.',
    submitted_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora UTC de recepción de la solicitud.',
    assigned_user_id bigint UNSIGNED NULL COMMENT 'Cuenta opcional de ventas o administración asignada para revisar la solicitud.',
    UNIQUE INDEX uq_quote_requests_submission_key (submission_key),
    CHECK (( CHAR_LENGTH( TRIM( submission_key ) ) > 0 )),
    CHECK (( CHAR_LENGTH( TRIM( first_name ) ) > 0 and CHAR_LENGTH( TRIM( last_name ) ) > 0 )),
    CHECK (( company_name is null or CHAR_LENGTH( TRIM( company_name ) ) > 0 )),
    CHECK (( CHAR_LENGTH( TRIM( phone_number ) ) between 6 and 20 and REGEXP_LIKE( phone_number , '^[+0-9 ()-]+$' ) )),
    CHECK (( REGEXP_LIKE( email , '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$' ) )),
    CHECK (( CHAR_LENGTH( TRIM( delivery_address ) ) > 0 )),
    CHECK (( status in ( 'new' , 'reviewing' , 'quoted' , 'closed' , 'cancelled' ) )),
    CONSTRAINT pk_quote_requests PRIMARY KEY (quote_request_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Cabecera de solicitud web y copia de los datos originales de contacto; no exige cliente registrado ni modifica inventario.';

CREATE INDEX ix_quote_requests_status_date ON quote_requests (status,submitted_at);

CREATE INDEX ix_quote_requests_email ON quote_requests (email);

-- Tabla: sale_details
CREATE TABLE sale_details (
    sale_detail_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    sale_id bigint UNSIGNED NOT NULL COMMENT 'Venta que contiene esta asignación de lote.',
    quotation_id bigint UNSIGNED NULL COMMENT 'Referencia opcional de cotización de origen, junto con quotation_detail_id.',
    quotation_detail_id bigint UNSIGNED NULL COMMENT 'Línea opcional de oferta de origen; garantiza coincidencia de cotización y lote.',
    collection_lot_id bigint UNSIGNED NOT NULL COMMENT 'Lote de acopio que se reservará y entregará.',
    quantity_kg decimal(12,2) NOT NULL COMMENT 'Kilogramos totales acordados para la línea; este valor determina la cantidad de su reserva completa.',
    unit_price decimal(12,2) NOT NULL COMMENT 'Precio histórico acordado en soles por kilogramo.',
    UNIQUE INDEX uq_sale_details_sale_lot (sale_id,collection_lot_id),
    UNIQUE INDEX uq_sale_details_reservation_anchor (sale_detail_id,sale_id,collection_lot_id),
    CHECK (( ( quotation_id is null and quotation_detail_id is null ) or ( quotation_id is not null and quotation_detail_id is not null ) )),
    CHECK (( quantity_kg > 0 )),
    CHECK (( unit_price > 0 )),
    CONSTRAINT pk_sale_details PRIMARY KEY (sale_detail_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Asignaciones de lotes que la aplicación debe mantener inmutables después de reservar; conservar cantidad y precio histórico.';

-- Tabla: sales
CREATE TABLE sales (
    sale_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    company_id bigint UNSIGNED NOT NULL COMMENT 'Cliente empresarial obligatorio registrado o seleccionado por el personal; requiere un RUC existente.',
    quotation_id bigint UNSIGNED NULL COMMENT 'Cotización aceptada opcional de la misma empresa; NULL indica venta manual directa.',
    operation_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Clave estable generada por el cliente para evitar ventas duplicadas por reintentos.',
    sale_date date NOT NULL COMMENT 'Fecha de registro comercial.',
    status varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'Estado de venta: pending, confirmed, partially_dispatched, completed o cancelled.',
    payment_method varchar(20) NOT NULL COMMENT 'Medio acordado: cash, bank_transfer, card, credit u other; no acredita la recepción del pago.',
    delivery_address varchar(255) NOT NULL COMMENT 'Copia de la dirección de entrega acordada para esta venta.',
    delivery_location_code char(6) NOT NULL COMMENT 'Distrito del destino de entrega acordado.',
    delivery_conditions varchar(255) NOT NULL COMMENT 'Condiciones de entrega acordadas, incluidos los acuerdos de entregas parciales cuando correspondan.',
    user_id bigint UNSIGNED NOT NULL COMMENT 'Cuenta de ventas o administración responsable del registro de la venta.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    UNIQUE INDEX uq_sales_operation_key (operation_key),
    UNIQUE INDEX uq_sales_quotation_anchor (sale_id,quotation_id),
    CHECK (( CHAR_LENGTH( TRIM( operation_key ) ) > 0 )),
    CHECK (( status in ( 'pending' , 'confirmed' , 'partially_dispatched' , 'completed' , 'cancelled' ) )),
    CHECK (( payment_method in ( 'cash' , 'bank_transfer' , 'card' , 'credit' , 'other' ) )),
    CHECK (( CHAR_LENGTH( TRIM( delivery_address ) ) > 0 and CHAR_LENGTH( TRIM( delivery_conditions ) ) > 0 )),
    CONSTRAINT pk_sales PRIMARY KEY (sale_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Ventas B2B registradas por el personal después de seleccionar o registrar la empresa y acordar las condiciones comerciales.';

CREATE INDEX ix_sales_company_date ON sales (company_id,sale_date);

CREATE INDEX ix_sales_status ON sales (status);

-- Tabla: users
CREATE TABLE users (
    user_id bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Clave primaria sustituta generada automáticamente por la base de datos.',
    username varchar(50) NOT NULL COMMENT 'Nombre de acceso único.',
    password_hash varchar(255) NOT NULL COMMENT 'Hash de contraseña generado y verificado por la aplicación; nunca almacenar contraseñas en texto plano.',
    contact_id bigint UNSIGNED NOT NULL COMMENT 'Contacto que identifica al titular de la cuenta.',
    role varchar(10) NOT NULL COMMENT 'Rol de aplicación: admin, sales, warehouse o editor.',
    status varchar(10) NOT NULL DEFAULT 'active' COMMENT 'Estado de la cuenta: active o inactive.',
    created_at datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'Fecha y hora de creación en UTC; la sesión de la aplicación debe usar UTC.',
    UNIQUE INDEX uq_users_username (username),
    CHECK (( CHAR_LENGTH( TRIM( username ) ) > 0 )),
    CHECK (( CHAR_LENGTH( TRIM( password_hash ) ) > 0 )),
    CHECK (( role in ( 'admin' , 'sales' , 'warehouse' , 'editor' ) )),
    CHECK (( status in ( 'active' , 'inactive' ) )),
    CONSTRAINT pk_users PRIMARY KEY (user_id)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Cuentas de aplicación; los roles son datos y el backend también debe aplicar la autorización.';

-- Cuentas de clientes del sitio web; las cuentas internas permanecen en users.
CREATE TABLE web_users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_web_users_email UNIQUE (email),
    CONSTRAINT uq_web_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. RELACIONES: CLAVES FORÁNEAS
-- Relación: fk_contacts_company (tabla: contacts)
ALTER TABLE contacts ADD CONSTRAINT fk_contacts_company FOREIGN KEY fk_contacts_company (company_id)
    REFERENCES companies (company_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_contacts_location (tabla: contacts)
ALTER TABLE contacts ADD CONSTRAINT fk_contacts_location FOREIGN KEY fk_contacts_location (location_code)
    REFERENCES geographic_locations (location_code)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_contacts_person (tabla: contacts)
ALTER TABLE contacts ADD CONSTRAINT fk_contacts_person FOREIGN KEY fk_contacts_person (person_id)
    REFERENCES people (person_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_dispatch_details_header (tabla: dispatch_details)
ALTER TABLE dispatch_details ADD CONSTRAINT fk_dispatch_details_header FOREIGN KEY fk_dispatch_details_header (dispatch_id,sale_id)
    REFERENCES dispatches (dispatch_id,sale_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_dispatch_details_reservation (tabla: dispatch_details)
ALTER TABLE dispatch_details ADD CONSTRAINT fk_dispatch_details_reservation FOREIGN KEY fk_dispatch_details_reservation (inventory_reservation_id,sale_id,collection_lot_id)
    REFERENCES inventory_reservations (inventory_reservation_id,sale_id,collection_lot_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_dispatches_author (tabla: dispatches)
ALTER TABLE dispatches ADD CONSTRAINT fk_dispatches_author FOREIGN KEY fk_dispatches_author (registered_by_user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_dispatches_sale (tabla: dispatches)
ALTER TABLE dispatches ADD CONSTRAINT fk_dispatches_sale FOREIGN KEY fk_dispatches_sale (sale_id)
    REFERENCES sales (sale_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_lots_campaign (tabla: collection_lots)
ALTER TABLE collection_lots ADD CONSTRAINT fk_lots_campaign FOREIGN KEY fk_lots_campaign (campaign_id)
    REFERENCES campaigns (campaign_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_lots_member (tabla: collection_lots)
ALTER TABLE collection_lots ADD CONSTRAINT fk_lots_member FOREIGN KEY fk_lots_member (member_id)
    REFERENCES members (member_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_lots_product (tabla: collection_lots)
ALTER TABLE collection_lots ADD CONSTRAINT fk_lots_product FOREIGN KEY fk_lots_product (product_id)
    REFERENCES products (product_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_lots_received_by_user (tabla: collection_lots)
ALTER TABLE collection_lots ADD CONSTRAINT fk_lots_received_by_user FOREIGN KEY fk_lots_received_by_user (received_by_user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_movements_author (tabla: inventory_movements)
ALTER TABLE inventory_movements ADD CONSTRAINT fk_movements_author FOREIGN KEY fk_movements_author (recorded_by_user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_movements_dispatch_line (tabla: inventory_movements)
ALTER TABLE inventory_movements ADD CONSTRAINT fk_movements_dispatch_line FOREIGN KEY fk_movements_dispatch_line (dispatch_detail_id,collection_lot_id)
    REFERENCES dispatch_details (dispatch_detail_id,collection_lot_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_movements_lot (tabla: inventory_movements)
ALTER TABLE inventory_movements ADD CONSTRAINT fk_movements_lot FOREIGN KEY fk_movements_lot (collection_lot_id)
    REFERENCES collection_lots (collection_lot_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_news_author (tabla: news)
ALTER TABLE news ADD CONSTRAINT fk_news_author FOREIGN KEY fk_news_author (author_user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_products_category (tabla: products)
ALTER TABLE products ADD CONSTRAINT fk_products_category FOREIGN KEY fk_products_category (category_id)
    REFERENCES categories (category_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quotations_author (tabla: quotations)
ALTER TABLE quotations ADD CONSTRAINT fk_quotations_author FOREIGN KEY fk_quotations_author (created_by_user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quotations_company (tabla: quotations)
ALTER TABLE quotations ADD CONSTRAINT fk_quotations_company FOREIGN KEY fk_quotations_company (company_id)
    REFERENCES companies (company_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quotations_contact (tabla: quotations)
ALTER TABLE quotations ADD CONSTRAINT fk_quotations_contact FOREIGN KEY fk_quotations_contact (contact_id)
    REFERENCES contacts (contact_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quotations_contact_company (tabla: quotations)
ALTER TABLE quotations ADD CONSTRAINT fk_quotations_contact_company FOREIGN KEY fk_quotations_contact_company (contact_id,company_id)
    REFERENCES contacts (contact_id,company_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quotations_location (tabla: quotations)
ALTER TABLE quotations ADD CONSTRAINT fk_quotations_location FOREIGN KEY fk_quotations_location (delivery_location_code)
    REFERENCES geographic_locations (location_code)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quotations_request (tabla: quotations)
ALTER TABLE quotations ADD CONSTRAINT fk_quotations_request FOREIGN KEY fk_quotations_request (quote_request_id)
    REFERENCES quote_requests (quote_request_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quote_details_header (tabla: quotation_details)
ALTER TABLE quotation_details ADD CONSTRAINT fk_quote_details_header FOREIGN KEY fk_quote_details_header (quotation_id)
    REFERENCES quotations (quotation_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quote_details_lot_product (tabla: quotation_details)
ALTER TABLE quotation_details ADD CONSTRAINT fk_quote_details_lot_product FOREIGN KEY fk_quote_details_lot_product (collection_lot_id,product_id)
    REFERENCES collection_lots (collection_lot_id,product_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quote_details_source_header (tabla: quotation_details)
ALTER TABLE quotation_details ADD CONSTRAINT fk_quote_details_source_header FOREIGN KEY fk_quote_details_source_header (quotation_id,quote_request_id)
    REFERENCES quotations (quotation_id,quote_request_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_quote_details_source_line (tabla: quotation_details)
ALTER TABLE quotation_details ADD CONSTRAINT fk_quote_details_source_line FOREIGN KEY fk_quote_details_source_line (quote_request_detail_id,quote_request_id,product_id)
    REFERENCES quote_request_details (quote_request_detail_id,quote_request_id,product_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_request_details_header (tabla: quote_request_details)
ALTER TABLE quote_request_details ADD CONSTRAINT fk_request_details_header FOREIGN KEY fk_request_details_header (quote_request_id)
    REFERENCES quote_requests (quote_request_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_request_details_product (tabla: quote_request_details)
ALTER TABLE quote_request_details ADD CONSTRAINT fk_request_details_product FOREIGN KEY fk_request_details_product (product_id)
    REFERENCES products (product_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_requests_assignee (tabla: quote_requests)
ALTER TABLE quote_requests ADD CONSTRAINT fk_requests_assignee FOREIGN KEY fk_requests_assignee (assigned_user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_reservations_author (tabla: inventory_reservations)
ALTER TABLE inventory_reservations ADD CONSTRAINT fk_reservations_author FOREIGN KEY fk_reservations_author (created_by_user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_reservations_sale_line (tabla: inventory_reservations)
ALTER TABLE inventory_reservations ADD CONSTRAINT fk_reservations_sale_line FOREIGN KEY fk_reservations_sale_line (sale_detail_id,sale_id,collection_lot_id)
    REFERENCES sale_details (sale_detail_id,sale_id,collection_lot_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sale_details_header (tabla: sale_details)
ALTER TABLE sale_details ADD CONSTRAINT fk_sale_details_header FOREIGN KEY fk_sale_details_header (sale_id)
    REFERENCES sales (sale_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sale_details_lot (tabla: sale_details)
ALTER TABLE sale_details ADD CONSTRAINT fk_sale_details_lot FOREIGN KEY fk_sale_details_lot (collection_lot_id)
    REFERENCES collection_lots (collection_lot_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sale_details_source_header (tabla: sale_details)
ALTER TABLE sale_details ADD CONSTRAINT fk_sale_details_source_header FOREIGN KEY fk_sale_details_source_header (sale_id,quotation_id)
    REFERENCES sales (sale_id,quotation_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sale_details_source_line (tabla: sale_details)
ALTER TABLE sale_details ADD CONSTRAINT fk_sale_details_source_line FOREIGN KEY fk_sale_details_source_line (quotation_detail_id,quotation_id,collection_lot_id)
    REFERENCES quotation_details (quotation_detail_id,quotation_id,collection_lot_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sales_company (tabla: sales)
ALTER TABLE sales ADD CONSTRAINT fk_sales_company FOREIGN KEY fk_sales_company (company_id)
    REFERENCES companies (company_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sales_location (tabla: sales)
ALTER TABLE sales ADD CONSTRAINT fk_sales_location FOREIGN KEY fk_sales_location (delivery_location_code)
    REFERENCES geographic_locations (location_code)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sales_quotation_company (tabla: sales)
ALTER TABLE sales ADD CONSTRAINT fk_sales_quotation_company FOREIGN KEY fk_sales_quotation_company (quotation_id,company_id)
    REFERENCES quotations (quotation_id,company_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_sales_user (tabla: sales)
ALTER TABLE sales ADD CONSTRAINT fk_sales_user FOREIGN KEY fk_sales_user (user_id)
    REFERENCES users (user_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Relación: fk_users_contact (tabla: users)
ALTER TABLE users ADD CONSTRAINT fk_users_contact FOREIGN KEY fk_users_contact (contact_id)
    REFERENCES contacts (contact_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Fin de la estructura y sus relaciones.


-- 4. CATÁLOGO INICIAL
-- Este bloque utiliza las tablas creadas en las secciones anteriores.
-- Agrega registros faltantes; conserva los registros existentes y sus estados.
USE apraq;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;

INSERT INTO categories (name, status)
SELECT 'Tubérculos', 'active'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Tubérculos');

INSERT INTO categories (name, status)
SELECT 'Frutas', 'active'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Frutas');

INSERT INTO products (name, category_id, status)
SELECT 'Papa Única', c.category_id, 'active'
FROM categories AS c
WHERE c.name = 'Tubérculos'
  AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Papa Única');

INSERT INTO products (name, category_id, status)
SELECT 'Camote Amarillo', c.category_id, 'active'
FROM categories AS c
WHERE c.name = 'Tubérculos'
  AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Camote Amarillo');

INSERT INTO products (name, category_id, status)
SELECT 'Maracuyá', c.category_id, 'active'
FROM categories AS c
WHERE c.name = 'Frutas'
  AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Maracuyá');

COMMIT;

-- 5. VERIFICACIÓN FINAL: se esperan tres productos en una instalación nueva.
SELECT p.product_id, p.name AS producto, c.name AS categoria,
       p.status AS estado_producto, c.status AS estado_categoria
FROM products AS p
JOIN categories AS c ON c.category_id = p.category_id
ORDER BY p.product_id;

SHOW COLUMNS FROM quote_requests;
SHOW COLUMNS FROM quote_request_details;
SHOW COLUMNS FROM web_users;

-- Se esperan 22 tablas base.
SELECT COUNT(*) AS total_tablas
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
