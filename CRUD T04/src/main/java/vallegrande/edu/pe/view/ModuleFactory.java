package vallegrande.edu.pe.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vallegrande.edu.pe.config.AppContext;
import vallegrande.edu.pe.dto.Forms;
import vallegrande.edu.pe.model.*;
import vallegrande.edu.pe.model.TableRow;
import vallegrande.edu.pe.repository.ApraqRepository;
import vallegrande.edu.pe.service.OperationsService;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

public final class ModuleFactory {
    private final ApraqRepository repository;
    private final OperationsService operations;
    private final UserSession session;

    public ModuleFactory(AppContext context, UserSession session) {
        this.repository = context.repository();
        this.operations = context.operations();
        this.session = session;
    }

    public Node dashboard() {
        VBox page = page("RESUMEN DE OPERACIONES", "Inicio", "Una lectura rápida del trabajo comercial y del inventario disponible.");
        GridPane cards = new GridPane(); cards.setHgap(16); cards.setVgap(16);
        for (int i=0;i<3;i++) { ColumnConstraints cc=new ColumnConstraints();cc.setPercentWidth(33.333);cc.setHgrow(Priority.ALWAYS);cards.getColumnConstraints().add(cc); }
        page.getChildren().add(cards);
        UiSupport.async(page, repository::dashboard, values -> {
            cards.add(metric("Solicitudes por atender", values.get("requests"), "Pendientes de revisión", "accent-green"),0,0);
            cards.add(metric("Cotizaciones pendientes", values.get("quotations"), "Por negociar o resolver", "accent-yellow"),1,0);
            cards.add(metric("Ventas por entregar", values.get("sales"), "Confirmadas o parciales", "accent-lime"),2,0);
            cards.add(metric("Stock físico", values.get("physical"), "kg registrados en movimientos", "accent-green"),0,1);
            cards.add(metric("Stock reservado", values.get("reserved"), "kg comprometidos pendientes", "accent-yellow"),1,1);
            cards.add(metric("Stock disponible", values.get("available"), "kg libres para nuevas ventas", "accent-lime"),2,1);
        });
        return page;
    }

    public Node catalogs() {
        DataTablePane categories = crudTable("Categorías", "Las dos clasificaciones vigentes y su disponibilidad.", StatusEntity.CATEGORIES);
        categories.addAction(primary("Nueva categoría", () -> categoryDialog(categories,null)));
        categories.addAction(secondary("Editar", () -> requireSelection(categories, row -> categoryDialog(categories,row))));
        DataTablePane products = crudTable("Productos", "Catálogo agrícola compartido por solicitudes, lotes y ventas.", StatusEntity.PRODUCTS);
        products.addAction(primary("Nuevo producto", () -> load(products, "categories", items -> productDialog(products,null,items))));
        products.addAction(secondary("Editar", () -> requireSelection(products,row -> load(products,"categories",items -> productDialog(products,row,items)))));
        DataTablePane locations = table("Ubicaciones geográficas", "Códigos oficiales disponibles para contactos y entregas.", "locations");
        return tabs("Categorías",categories,"Productos",products,"Ubicaciones",locations);
    }

    public Node producers() {
        DataTablePane members = crudTable("Productores asociados", "DNI, datos de contacto y estado de los miembros que entregan producción.", StatusEntity.MEMBERS);
        members.addAction(primary("Nuevo productor",()->memberDialog(members,null)));
        members.addAction(secondary("Editar",()->requireSelection(members,row->memberDialog(members,row))));
        DataTablePane campaigns = table("Campañas agrícolas", "Períodos que validan la fecha de cada lote recibido.", "campaigns");
        campaigns.addAction(primary("Nueva campaña",()->campaignDialog(campaigns,null)));
        campaigns.addAction(secondary("Editar",()->requireSelection(campaigns,row->campaignDialog(campaigns,row))));
        return tabs("Productores",members,"Campañas",campaigns);
    }

    public Node clients() {
        DataTablePane companies=crudTable("Empresas clientes","Empresas verificadas por RUC para ventas B2B.",StatusEntity.COMPANIES);
        companies.addAction(primary("Nueva empresa",()->companyDialog(companies,null)));
        companies.addAction(secondary("Editar",()->requireSelection(companies,row->companyDialog(companies,row))));
        DataTablePane people=table("Personas","Personas naturales que pueden actuar como contactos.","people");
        people.addAction(primary("Nueva persona",()->personDialog(people,null)));
        people.addAction(secondary("Editar",()->requireSelection(people,row->personDialog(people,row))));
        DataTablePane contacts=table("Contactos","Canales de contacto asociados opcionalmente a una empresa.","contacts");
        contacts.addAction(primary("Nuevo contacto",()->loadMany(contacts,List.of("people","companies","locations"),data->contactDialog(contacts,null,data))));
        contacts.addAction(secondary("Editar",()->requireSelection(contacts,row->loadMany(contacts,List.of("people","companies","locations"),data->contactDialog(contacts,row,data)))));
        return tabs("Empresas",companies,"Personas",people,"Contactos",contacts);
    }

    public Node requests() {
        DataTablePane pane=table("Solicitudes web","Bandeja en vivo con contacto, destino, productos solicitados y responsable.","requests");
        pane.addAction(primary("Asignar y actualizar",()->requireSelection(pane,row->UiSupport.async(pane,repository::users,users->requestDialog(pane,row,users)))));
        return pane;
    }

    public Node quotations() {
        DataTablePane pane=table("Cotizaciones","Ofertas comerciales; crear o aceptar una cotización no modifica el inventario.","quotations");
        pane.addAction(primary("Nueva cotización",()->loadMany(pane,List.of("requests","contacts","companies","locations","products","lots"),data->quotationDialog(pane,data))));
        pane.addAction(secondary("Cambiar estado",()->requireSelection(pane,row->quotationStatusDialog(pane,row))));
        return pane;
    }

    public Node sales() {
        DataTablePane pane=table("Ventas","Ventas B2B pendientes, confirmadas y entregadas; confirmar crea reservas atómicas.","sales");
        pane.addAction(primary("Nueva venta",()->loadMany(pane,List.of("accepted_quotations","companies","locations","lots"),data->saleDialog(pane,data))));
        pane.addAction(secondary("Confirmar",()->requireSelection(pane,row->{if(UiSupport.confirm("Confirmar venta","Se bloquearán los lotes y se reservará stock para todas las líneas."))UiSupport.asyncRun(pane,()->operations.confirmSale(row.id("id"),userId()),()->{pane.reload();UiSupport.info("Venta confirmada","Las reservas fueron creadas correctamente.");});})));
        pane.addAction(secondary("Cancelar",()->requireSelection(pane,row->{if(UiSupport.confirm("Cancelar venta","Solo es posible si no existen despachos. Las reservas activas serán liberadas."))UiSupport.asyncRun(pane,()->operations.cancelSale(row.id("id")),pane::reload);})));
        return pane;
    }

    public Node collection() {
        DataTablePane pane=table("Acopio","Cada lote recibido genera exactamente un movimiento de ingreso con el mismo peso.","lots");
        pane.addAction(primary("Recibir lote",()->loadMany(pane,List.of("members","products","campaigns"),data->lotDialog(pane,data))));
        return pane;
    }

    public Node inventory() {
        DataTablePane balances=table("Saldos por lote","Stock físico menos reservas pendientes, calculado exclusivamente desde movimientos y reservas.","inventory");
        balances.addAction(primary("Registrar ajuste",()->requireSelection(balances,row->adjustmentDialog(balances,row))));
        DataTablePane movements=table("Movimientos","Libro físico append-only: ingresos, despachos y ajustes justificados.","movements");
        DataTablePane reservations=table("Reservas","Compromisos por línea de venta y sus cantidades pendientes.","reservations");
        return tabs("Saldos",balances,"Movimientos",movements,"Reservas",reservations);
    }

    public Node dispatches() {
        DataTablePane pane=table("Despachos","Entregas parciales o completas vinculadas a reservas y movimientos físicos.","dispatches");
        pane.addAction(primary("Registrar despacho",()->load(pane,"sales",sales->dispatchDialog(pane,sales))));
        return pane;
    }

    public Node news() {
        DataTablePane pane=table("Noticias","Borradores, publicaciones y archivo del contenido del sitio web.","news");
        pane.addAction(primary("Nueva noticia",()->newsDialog(pane,null)));
        pane.addAction(secondary("Editar",()->requireSelection(pane,row->newsDialog(pane,row))));
        return pane;
    }

    public Node users() {
        if (!session.isAdmin()) {
            return page("ACCESO RESTRINGIDO", "Usuarios", "Solo un administrador puede gestionar cuentas y roles.");
        }
        DataTablePane pane = crudTable("Usuarios y roles", "Cuentas de acceso del personal. Cada rol habilita únicamente las operaciones que le corresponden.", StatusEntity.USERS);
        pane.addAction(primary("Nuevo usuario", () -> load(pane, "contacts", contacts -> userDialog(pane, null, contacts))));
        pane.addAction(secondary("Editar", () -> requireSelection(pane,
                row -> load(pane, "contacts", contacts -> userDialog(pane, row, contacts)))));
        return pane;
    }

    public Node reports() {
        VBox page=page("EXPORTACIÓN Y CONTROL","Reportes","Filtre cualquier consulta principal y expórtela como CSV UTF-8.");
        ComboBox<String> report=new ComboBox<>(FXCollections.observableArrayList("requests","quotations","sales","dispatches","inventory","members"));report.getSelectionModel().selectFirst();
        StackPane host=new StackPane();VBox.setVgrow(host,Priority.ALWAYS);
        Button show=primary("Consultar",()->host.getChildren().setAll(reportPane(report.getValue())));
        HBox controls=new HBox(10,new Label("Consulta"),report,show);controls.setAlignment(Pos.CENTER_LEFT);controls.getStyleClass().add("report-controls");
        page.getChildren().addAll(controls,host);host.getChildren().setAll(reportPane(report.getValue()));return page;
    }

    private DataTablePane reportPane(String view){
        DataTablePane pane=table("Resultado del reporte","Use la búsqueda para aplicar un filtro visible antes de exportar.",view);
        pane.addAction(primary("Exportar CSV",()->exportCsv(pane,view)));
        return pane;
    }

    private void categoryDialog(DataTablePane pane,TableRow row){
        TextField name=text(row,"Nombre");ComboBox<String>status=choice(List.of("active","inactive"),value(row,"Estado","active"));GridPane form=UiSupport.form();UiSupport.field(form,0,"Nombre interno",name);UiSupport.field(form,1,"Estado",status);
        UiSupport.dialog(row==null?"Nueva categoría":"Editar categoría",form,()->{Long rowId=id(row);String nameValue=name.getText(),statusValue=status.getValue();return (Runnable)()->repository.saveCategory(rowId,nameValue,statusValue);}).ifPresent(action->save(pane,action));
    }

    private void productDialog(DataTablePane pane,TableRow row,List<LookupItem> categories){
        categories = includeCurrent(categories, hiddenId(row, "category_id"), value(row, "Categoría", ""));
        TextField name=text(row,"Producto");ComboBox<LookupItem>category=combo(categories,hiddenId(row,"category_id"));ComboBox<String>status=choice(List.of("active","inactive"),value(row,"Estado","active"));GridPane form=UiSupport.form();UiSupport.field(form,0,"Nombre interno",name);UiSupport.field(form,1,"Categoría",category);UiSupport.field(form,2,"Estado",status);
        UiSupport.dialog(row==null?"Nuevo producto":"Editar producto",form,()->{Long rowId=id(row);String nameValue=name.getText(),statusValue=status.getValue();long categoryId=required(category,"categoría").id();return (Runnable)()->repository.saveProduct(rowId,nameValue,categoryId,statusValue);}).ifPresent(action->save(pane,action));
    }

    private void memberDialog(DataTablePane pane,TableRow row){
        TextField dni=text(row,"DNI"),first=text(row,"Nombres"),last=text(row,"Apellidos"),phone=text(row,"Teléfono"),address=text(row,"Dirección");DatePicker entry=date(row,"Ingreso",LocalDate.now());ComboBox<String>status=choice(List.of("active","inactive"),value(row,"Estado","active"));GridPane form=UiSupport.form();UiSupport.field(form,0,"DNI (8 dígitos)",dni);UiSupport.field(form,1,"Nombres",first);UiSupport.field(form,2,"Apellidos",last);UiSupport.field(form,3,"Teléfono",phone);UiSupport.field(form,4,"Fecha de ingreso",entry);UiSupport.field(form,5,"Dirección",address);UiSupport.field(form,6,"Estado",status);
        UiSupport.dialog(row==null?"Nuevo productor":"Editar productor",form,()->{Long rowId=id(row);String dniValue=dni.getText(),firstValue=first.getText(),lastValue=last.getText(),phoneValue=phone.getText(),addressValue=address.getText(),statusValue=status.getValue();LocalDate entryValue=entry.getValue();return (Runnable)()->repository.saveMember(rowId,dniValue,firstValue,lastValue,phoneValue,entryValue,addressValue,statusValue);}).ifPresent(a->save(pane,a));
    }

    private void campaignDialog(DataTablePane pane,TableRow row){
        TextField name=text(row,"Campaña");DatePicker start=date(row,"Inicio",LocalDate.now()),end=date(row,"Fin",LocalDate.now().plusMonths(4));ComboBox<String>status=choice(List.of("planned","active","closed","cancelled"),value(row,"Estado","planned"));GridPane form=UiSupport.form();UiSupport.field(form,0,"Nombre",name);UiSupport.field(form,1,"Inicio",start);UiSupport.field(form,2,"Fin",end);UiSupport.field(form,3,"Estado",status);
        UiSupport.dialog(row==null?"Nueva campaña":"Editar campaña",form,()->{Long rowId=id(row);String nameValue=name.getText(),statusValue=status.getValue();LocalDate startValue=start.getValue(),endValue=end.getValue();return (Runnable)()->repository.saveCampaign(rowId,nameValue,startValue,endValue,statusValue);}).ifPresent(a->save(pane,a));
    }

    private void companyDialog(DataTablePane pane,TableRow row){
        TextField name=text(row,"Razón social"),ruc=text(row,"RUC");ComboBox<String>status=choice(List.of("active","inactive"),value(row,"Estado","active"));GridPane form=UiSupport.form();UiSupport.field(form,0,"Razón social",name);UiSupport.field(form,1,"RUC (11 dígitos)",ruc);UiSupport.field(form,2,"Estado",status);
        UiSupport.dialog(row==null?"Nueva empresa":"Editar empresa",form,()->{Long rowId=id(row);String nameValue=name.getText(),rucValue=ruc.getText(),statusValue=status.getValue();return (Runnable)()->repository.saveCompany(rowId,nameValue,rucValue,statusValue);}).ifPresent(a->save(pane,a));
    }

    private void personDialog(DataTablePane pane,TableRow row){
        TextField first=text(row,"Nombres"),last=text(row,"Apellidos"),number=text(row,"Documento");ComboBox<String>type=choice(List.of("","dni","ce"),value(row,"Tipo doc.",""));GridPane form=UiSupport.form();UiSupport.field(form,0,"Nombres",first);UiSupport.field(form,1,"Apellidos",last);UiSupport.field(form,2,"Tipo de documento",type);UiSupport.field(form,3,"Número",number);
        UiSupport.dialog(row==null?"Nueva persona":"Editar persona",form,()->{Long rowId=id(row);String firstValue=first.getText(),lastValue=last.getText(),typeValue=type.getValue(),numberValue=number.getText();return (Runnable)()->repository.savePerson(rowId,firstValue,lastValue,typeValue,numberValue);}).ifPresent(a->save(pane,a));
    }

    private void contactDialog(DataTablePane pane,TableRow row,Map<String,List<LookupItem>> data){
        data.put("companies", includeCurrent(data.get("companies"), hiddenId(row, "company_id"), value(row, "Empresa", "")));
        ComboBox<LookupItem>person=combo(data.get("people"),hiddenId(row,"person_id")),company=nullableCombo(data.get("companies"),hiddenId(row,"company_id")),location=nullableCombo(data.get("locations"),locationId(row,"Ubicación"));TextField phone=text(row,"Teléfono"),email=text(row,"Correo"),address=text(row,"Dirección");GridPane form=UiSupport.form();UiSupport.field(form,0,"Persona",person);UiSupport.field(form,1,"Empresa (opcional)",company);UiSupport.field(form,2,"Teléfono",phone);UiSupport.field(form,3,"Correo",email);UiSupport.field(form,4,"Dirección",address);UiSupport.field(form,5,"Ubicación",location);
        UiSupport.dialog(row==null?"Nuevo contacto":"Editar contacto",form,()->{Long rowId=id(row),companyId=selectedId(company);long personId=required(person,"persona").id();String phoneValue=phone.getText(),emailValue=email.getText(),addressValue=address.getText(),locationValue=locationCode(location.getValue());return (Runnable)()->repository.saveContact(rowId,personId,companyId,phoneValue,emailValue,addressValue,locationValue);}).ifPresent(a->save(pane,a));
    }

    private void requestDialog(DataTablePane pane,TableRow row,List<DbUser> users){
        ComboBox<DbUser>assignee=new ComboBox<>(FXCollections.observableArrayList(users.stream().filter(u->u.status().equals("active")).toList()));Long current=hiddenId(row,"assigned_user_id");if(current!=null)assignee.getItems().stream().filter(u->u.id()==current).findFirst().ifPresent(assignee::setValue);ComboBox<String>status=choice(List.of("new","reviewing","quoted","closed","cancelled"),value(row,"Estado","new"));GridPane form=UiSupport.form();UiSupport.field(form,0,"Responsable",assignee);UiSupport.field(form,1,"Estado",status);
        UiSupport.dialog("Actualizar solicitud #"+row.id("id"),form,()->{long requestId=row.id("id");Long assigneeId=assignee.getValue()==null?null:assignee.getValue().id();String statusValue=status.getValue();return (Runnable)()->repository.updateRequest(requestId,assigneeId,statusValue);}).ifPresent(a->save(pane,a));
    }

    private void quotationStatusDialog(DataTablePane pane,TableRow row){
        ComboBox<String>status=choice(List.of("pending","in_negotiation","accepted","rejected","expired","cancelled"),value(row,"Estado","pending"));GridPane form=UiSupport.form();UiSupport.field(form,0,"Estado",status);UiSupport.dialog("Estado de cotización #"+row.id("id"),form,()->{long quotationId=row.id("id");String statusValue=status.getValue();return (Runnable)()->repository.updateQuotationStatus(quotationId,statusValue);}).ifPresent(a->save(pane,a));
    }

    private void lotDialog(DataTablePane pane,Map<String,List<LookupItem>> data){
        ComboBox<LookupItem>member=combo(data.get("members"),null),product=combo(data.get("products"),null),campaign=combo(data.get("campaigns"),null);DatePicker entry=new DatePicker(LocalDate.now());TextField weight=new TextField(),key=new TextField(UUID.randomUUID().toString());weight.setPromptText("250.00");GridPane form=UiSupport.form();UiSupport.field(form,0,"Productor",member);UiSupport.field(form,1,"Producto",product);UiSupport.field(form,2,"Campaña",campaign);UiSupport.field(form,3,"Fecha de ingreso",entry);UiSupport.field(form,4,"Peso kg",weight);UiSupport.field(form,5,"Clave de operación",key);
        UiSupport.dialog("Recibir lote",form,()->new Forms.Lot(required(member,"productor").id(),required(product,"producto").id(),required(campaign,"campaña").id(),entry.getValue(),UiSupport.decimal(weight.getText(),"El peso"),userId(),key.getText())).ifPresent(lot->saveResult(pane,()->operations.receiveLot(lot),id->{UiSupport.info("Lote recibido","Se registró el lote #"+id+" y su movimiento de ingreso.");}));
    }

    private void adjustmentDialog(DataTablePane pane,TableRow row){
        ComboBox<String>type=choice(List.of("adjustment_in","adjustment_out"),"adjustment_in");TextField qty=new TextField(),reason=new TextField(),key=new TextField(UUID.randomUUID().toString());GridPane form=UiSupport.form();UiSupport.field(form,0,"Lote",new Label("#"+row.id("id")+" · "+UiSupport.text(row.get("Producto"))));UiSupport.field(form,1,"Tipo",type);UiSupport.field(form,2,"Cantidad kg",qty);UiSupport.field(form,3,"Motivo",reason);UiSupport.field(form,4,"Clave de operación",key);
        UiSupport.dialog("Ajustar inventario",form,()->{long lotId=row.id("id"),currentUserId=userId();BigDecimal quantity=UiSupport.decimal(qty.getText(),"La cantidad");boolean inbound=type.getValue().equals("adjustment_in");String reasonValue=reason.getText(),keyValue=key.getText();return (Runnable)()->operations.adjust(lotId,quantity,inbound,reasonValue,currentUserId,keyValue);}).ifPresent(a->save(pane,a));
    }

    private void newsDialog(DataTablePane pane,TableRow row){
        TextField title=text(row,"Título"),photos=text(row,"Fotos JSON");TextArea body=new TextArea(value(row,"Contenido",""));body.setPrefRowCount(8);ComboBox<String>status=choice(List.of("draft","published","archived"),value(row,"Estado","draft"));GridPane form=UiSupport.form();UiSupport.field(form,0,"Título",title);UiSupport.field(form,1,"Contenido",body);UiSupport.field(form,2,"Fotos (arreglo JSON opcional)",photos);UiSupport.field(form,3,"Estado",status);
        UiSupport.dialog(row==null?"Nueva noticia":"Editar noticia",form,()->{Long rowId=id(row);long currentUserId=userId();String titleValue=title.getText(),bodyValue=body.getText(),photosValue=photos.getText(),statusValue=status.getValue();return (Runnable)()->repository.saveNews(rowId,titleValue,bodyValue,photosValue,statusValue,currentUserId);}).ifPresent(a->save(pane,a));
    }

    private void userDialog(DataTablePane pane, TableRow row, List<LookupItem> contacts) {
        TextField username = text(row, "Usuario");
        ComboBox<LookupItem> contact = combo(contacts, hiddenId(row, "contact_id"));
        PasswordField password = new PasswordField();
        PasswordField confirmation = new PasswordField();
        password.setPromptText(row == null ? "Mínimo 8 caracteres" : "Dejar vacío para conservarla");
        confirmation.setPromptText("Repita la contraseña");
        ComboBox<String> role = choice(List.of("admin", "sales", "warehouse", "editor"), value(row, "Rol", "sales"));
        ComboBox<String> status = choice(List.of("active", "inactive"), value(row, "Estado", "active"));
        GridPane form = UiSupport.form();
        UiSupport.field(form, 0, "Usuario", username);
        UiSupport.field(form, 1, "Persona y contacto", contact);
        UiSupport.field(form, 2, row == null ? "Contraseña" : "Nueva contraseña (opcional)", password);
        UiSupport.field(form, 3, "Confirmar contraseña", confirmation);
        UiSupport.field(form, 4, "Rol", role);
        UiSupport.field(form, 5, "Estado", status);
        UiSupport.dialog(row == null ? "Registrar usuario" : "Editar usuario", form, () -> {
            String passwordValue = password.getText();
            if (!Objects.equals(passwordValue, confirmation.getText())) {
                throw new IllegalArgumentException("Las contraseñas no coinciden");
            }
            Long rowId = id(row);
            String usernameValue = username.getText(), roleValue = role.getValue(), statusValue = status.getValue();
            if (rowId != null && rowId == userId() && "inactive".equals(statusValue)) {
                throw new IllegalArgumentException("No puede desactivar su propia cuenta");
            }
            long contactId = required(contact, "persona y contacto").id();
            return (Runnable) () -> repository.saveUser(rowId, usernameValue, passwordValue,
                    contactId, roleValue, statusValue);
        }).ifPresent(action -> save(pane, action));
    }

    private void quotationDialog(DataTablePane pane,Map<String,List<LookupItem>> data){
        ComboBox<LookupItem>request=nullableCombo(data.get("requests"),null),contact=combo(data.get("contacts"),null),company=nullableCombo(data.get("companies"),null),location=combo(data.get("locations"),null),product=combo(data.get("products"),null),lot=combo(data.get("lots"),null);DatePicker date=new DatePicker(LocalDate.now()),valid=new DatePicker(LocalDate.now().plusDays(15));TextField address=new TextField(),message=new TextField(),qty=new TextField(),price=new TextField(),packaging=new TextField();ComboBox<TableRow>requestLine=new ComboBox<>();requestLine.setPromptText("Línea de solicitud (opcional)");requestLine.setConverter(rowConverter("Producto","Solicitado kg"));ListView<Forms.QuoteLine>lines=new ListView<>();lines.setPrefHeight(120);lines.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(Forms.QuoteLine line,boolean empty){super.updateItem(line,empty);setText(empty||line==null?null:"Lote #"+line.lotId()+" · "+line.quantityKg()+" kg · S/ "+line.unitPrice());}});
        request.setOnAction(e->{LookupItem selected=request.getValue();requestLine.getItems().clear();if(selected!=null)UiSupport.async(pane,()->repository.requestLines(selected.id()),rows->requestLine.getItems().setAll(rows));});
        Button add=secondary("Agregar línea",()->{TableRow source=requestLine.getValue();long productId=source==null?required(product,"producto").id():source.id("product_id");Long requestId=source==null?null:source.id("request_id");Long detailId=source==null?null:source.id("id");Forms.QuoteLine line=new Forms.QuoteLine(requestId,detailId,productId,required(lot,"lote").id(),UiSupport.decimal(qty.getText(),"La cantidad"),UiSupport.decimal(price.getText(),"El precio"),packaging.getText());lines.getItems().add(line);});Button remove=secondary("Quitar seleccionada",()->lines.getItems().remove(lines.getSelectionModel().getSelectedItem()));
        GridPane form=UiSupport.form();int r=0;UiSupport.field(form,r++,"Solicitud de origen",request);UiSupport.field(form,r++,"Contacto",contact);UiSupport.field(form,r++,"Empresa",company);UiSupport.field(form,r++,"Fecha",date);UiSupport.field(form,r++,"Válida hasta",valid);UiSupport.field(form,r++,"Dirección de entrega",address);UiSupport.field(form,r++,"Ubicación",location);UiSupport.field(form,r++,"Mensaje",message);UiSupport.field(form,r++,"Línea solicitada",requestLine);UiSupport.field(form,r++,"Producto manual",product);UiSupport.field(form,r++,"Lote",lot);UiSupport.field(form,r++,"Cantidad kg",qty);UiSupport.field(form,r++,"Precio por kg",price);UiSupport.field(form,r++,"Presentación",packaging);UiSupport.field(form,r++,"Líneas",new VBox(8,new HBox(8,add,remove),lines));
        UiSupport.dialog("Nueva cotización",form,()->{if(lines.getItems().isEmpty())throw new IllegalArgumentException("Agregue al menos una línea");Long source=request.getValue()==null?null:request.getValue().id();return new Forms.Quotation(source,required(contact,"contacto").id(),selectedId(company),date.getValue(),valid.getValue(),address.getText(),locationCode(required(location,"ubicación")),message.getText(),userId(),List.copyOf(lines.getItems()));}).ifPresent(quotation->saveResult(pane,()->operations.createQuotation(quotation),id->UiSupport.info("Cotización registrada","Se creó la cotización #"+id+" sin reservar inventario.")));
    }

    private void saleDialog(DataTablePane pane,Map<String,List<LookupItem>> data){
        ComboBox<LookupItem>quotation=nullableCombo(data.get("accepted_quotations"),null),company=combo(data.get("companies"),null),location=combo(data.get("locations"),null),lot=combo(data.get("lots"),null);ComboBox<TableRow>quoteLine=new ComboBox<>();quoteLine.setConverter(rowConverter("Producto","Cantidad kg"));DatePicker date=new DatePicker(LocalDate.now());ComboBox<String>payment=choice(List.of("cash","bank_transfer","card","credit","other"),"bank_transfer");TextField address=new TextField(),conditions=new TextField("Entrega coordinada con el cliente"),key=new TextField(UUID.randomUUID().toString()),qty=new TextField(),price=new TextField();ListView<Forms.SaleLine>lines=new ListView<>();lines.setPrefHeight(120);lines.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(Forms.SaleLine line,boolean empty){super.updateItem(line,empty);setText(empty||line==null?null:"Lote #"+line.lotId()+" · "+line.quantityKg()+" kg · S/ "+line.unitPrice());}});
        quotation.setOnAction(e->{quoteLine.getItems().clear();if(quotation.getValue()!=null)UiSupport.async(pane,()->repository.quotationLines(quotation.getValue().id()),rows->quoteLine.getItems().setAll(rows));});
        Button add=secondary("Agregar línea",()->{TableRow source=quoteLine.getValue();Long quoteId=source==null?null:source.id("quotation_id");Long detailId=source==null?null:source.id("id");long lotId=source==null?required(lot,"lote").id():source.id("lot_id");BigDecimal quantity=UiSupport.decimal(qty.getText(),"La cantidad");BigDecimal unit=UiSupport.decimal(price.getText(),"El precio");lines.getItems().add(new Forms.SaleLine(quoteId,detailId,lotId,quantity,unit));});Button remove=secondary("Quitar seleccionada",()->lines.getItems().remove(lines.getSelectionModel().getSelectedItem()));
        GridPane form=UiSupport.form();int r=0;UiSupport.field(form,r++,"Cotización aceptada",quotation);UiSupport.field(form,r++,"Empresa cliente",company);UiSupport.field(form,r++,"Fecha",date);UiSupport.field(form,r++,"Medio de pago",payment);UiSupport.field(form,r++,"Dirección",address);UiSupport.field(form,r++,"Ubicación",location);UiSupport.field(form,r++,"Condiciones",conditions);UiSupport.field(form,r++,"Clave de operación",key);UiSupport.field(form,r++,"Línea cotizada",quoteLine);UiSupport.field(form,r++,"Lote manual",lot);UiSupport.field(form,r++,"Cantidad kg",qty);UiSupport.field(form,r++,"Precio por kg",price);UiSupport.field(form,r++,"Líneas",new VBox(8,new HBox(8,add,remove),lines));
        UiSupport.dialog("Nueva venta",form,()->{if(lines.getItems().isEmpty())throw new IllegalArgumentException("Agregue al menos una línea");Long quote=quotation.getValue()==null?null:quotation.getValue().id();return new Forms.Sale(required(company,"empresa").id(),quote,key.getText(),date.getValue(),payment.getValue(),address.getText(),locationCode(required(location,"ubicación")),conditions.getText(),userId(),List.copyOf(lines.getItems()));}).ifPresent(sale->saveResult(pane,()->operations.createSale(sale),id->UiSupport.info("Venta registrada","Se creó la venta pendiente #"+id+". Confírmela para reservar stock.")));
    }

    private void dispatchDialog(DataTablePane pane,List<LookupItem> sales){
        ComboBox<LookupItem>sale=combo(sales,null);ComboBox<ReservationOption>reservation=new ComboBox<>();reservation.setMaxWidth(Double.MAX_VALUE);TextField qty=new TextField(),reference=new TextField(),notes=new TextField(),key=new TextField(UUID.randomUUID().toString());sale.setOnAction(e->{reservation.getItems().clear();if(sale.getValue()!=null)UiSupport.async(pane,()->operations.pendingReservations(sale.getValue().id()),rows->{reservation.getItems().setAll(rows);if(!rows.isEmpty())reservation.getSelectionModel().selectFirst();});});GridPane form=UiSupport.form();UiSupport.field(form,0,"Venta",sale);UiSupport.field(form,1,"Reserva",reservation);UiSupport.field(form,2,"Cantidad kg",qty);UiSupport.field(form,3,"Referencia",reference);UiSupport.field(form,4,"Notas",notes);UiSupport.field(form,5,"Clave de operación",key);
        UiSupport.dialog("Registrar despacho",form,()->{ReservationOption selected=required(reservation,"reserva");return new Forms.Dispatch(required(sale,"venta").id(),key.getText(),LocalDateTime.now(ZoneOffset.UTC),reference.getText(),notes.getText(),userId(),List.of(new Forms.DispatchLine(selected.reservationId(),selected.lotId(),UiSupport.decimal(qty.getText(),"La cantidad"))));}).ifPresent(dispatch->saveResult(pane,()->operations.dispatch(dispatch),id->UiSupport.info("Despacho registrado","Se creó el despacho #"+id+" y su movimiento físico.")));
    }

    private void exportCsv(DataTablePane pane,String name){
        if(pane.visibleRows().isEmpty()){UiSupport.info("Sin datos","No hay registros visibles para exportar.");return;}FileChooser chooser=new FileChooser();chooser.setTitle("Guardar reporte CSV");chooser.setInitialFileName("apraq-"+name+"-"+LocalDate.now()+".csv");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV","*.csv"));File file=chooser.showSaveDialog(pane.getScene().getWindow());if(file==null)return;
        try{List<TableRow> rows=pane.visibleRows();List<String>headers=new ArrayList<>(rows.get(0).values().keySet());StringBuilder csv=new StringBuilder("\uFEFF");csv.append(String.join(",",headers.stream().map(this::csv).toList())).append('\n');for(TableRow row:rows){csv.append(String.join(",",headers.stream().map(h->csv(UiSupport.text(row.get(h)))).toList())).append('\n');}Files.writeString(file.toPath(),csv,StandardCharsets.UTF_8);UiSupport.info("Reporte exportado","Archivo guardado en "+file.getAbsolutePath());}catch(Exception exception){UiSupport.error("No se pudo exportar",UiSupport.rootMessage(exception));}
    }

    private String csv(String value){return "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";}
    private DataTablePane crudTable(String title, String description, StatusEntity entity) {
        DataTablePane pane = table(title, description + " Use el filtro Estado para consultar y restaurar los registros inactivos.", entity.table());
        pane.enableStatusActions(row -> {
            boolean activate = "inactive".equals(row.get("Estado"));
            save(pane, () -> repository.setActive(entity, row.id("id"), activate, session));
        }, row -> entity != StatusEntity.USERS || row.id("id") != userId());
        return pane;
    }
    private DataTablePane table(String title,String description,String view){return new DataTablePane(title,description,()->repository.rows(view));}
    private VBox page(String eyebrow,String title,String description){VBox box=new VBox(18);box.setPadding(new Insets(32));box.getStyleClass().add("module-page");Label e=new Label(eyebrow);e.getStyleClass().add("eyebrow");Label h=new Label(title);h.getStyleClass().add("page-title");Label d=new Label(description);d.getStyleClass().add("page-description");d.setWrapText(true);box.getChildren().addAll(e,h,d);return box;}
    private VBox metric(String title,BigDecimal value,String note,String accent){VBox box=new VBox(8);box.getStyleClass().addAll("metric-card",accent);Label t=new Label(title),v=new Label(value.stripTrailingZeros().toPlainString()),n=new Label(note);t.getStyleClass().add("metric-title");v.getStyleClass().add("metric-value");n.getStyleClass().add("metric-note");box.getChildren().addAll(t,v,n);return box;}
    private TabPane tabs(Object...pairs){TabPane tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);for(int i=0;i<pairs.length;i+=2)tabs.getTabs().add(new Tab(String.valueOf(pairs[i]),(Node)pairs[i+1]));return tabs;}
    private Button primary(String text,Runnable action){Button button=new Button(text);button.getStyleClass().add("primary-button");button.setOnAction(e->runSafe(action));return button;}
    private Button secondary(String text,Runnable action){Button button=new Button(text);button.getStyleClass().add("quiet-button");button.setOnAction(e->runSafe(action));return button;}
    private void runSafe(Runnable action){try{action.run();}catch(Exception exception){UiSupport.error("Revise la operación",UiSupport.rootMessage(exception));}}
    private void save(DataTablePane pane,Runnable action){UiSupport.asyncRun(pane,action,pane::reload);}
    private <T> void saveResult(DataTablePane pane,Supplier<T> action,java.util.function.Consumer<T> success){UiSupport.async(pane,action,result->{pane.reload();success.accept(result);});}
    private void load(DataTablePane pane,String type,java.util.function.Consumer<List<LookupItem>> done){UiSupport.async(pane,()->repository.lookup(type),done);}
    private void loadMany(DataTablePane pane,List<String> types,java.util.function.Consumer<Map<String,List<LookupItem>>> done){UiSupport.async(pane,()->{Map<String,List<LookupItem>>data=new HashMap<>();for(String type:types)data.put(type,repository.lookup(type));return data;},done);}
    private void requireSelection(DataTablePane pane,java.util.function.Consumer<TableRow> action){TableRow row=pane.selected();if(row==null)UiSupport.info("Seleccione un registro","Elija una fila de la tabla para continuar.");else action.accept(row);}
    private TextField text(TableRow row,String key){return new TextField(value(row,key,""));}
    private List<LookupItem> includeCurrent(List<LookupItem> values, Long id, String label) {
        List<LookupItem> result = new ArrayList<>(values);
        if (id != null && result.stream().noneMatch(item -> item.id() == id)) {
            result.add(new LookupItem(id, label + " (inactiva)"));
        }
        return result;
    }
    private DatePicker date(TableRow row,String key,LocalDate fallback){Object value=row==null?null:row.get(key);LocalDate parsed=value instanceof LocalDate d?d:value instanceof java.sql.Date d?d.toLocalDate():fallback;return new DatePicker(parsed);}
    private ComboBox<String> choice(List<String> values,String selected){ComboBox<String>combo=new ComboBox<>(FXCollections.observableArrayList(values));combo.setValue(selected);combo.setMaxWidth(Double.MAX_VALUE);return combo;}
    private ComboBox<LookupItem> combo(List<LookupItem> values,Long selected){ComboBox<LookupItem>combo=new ComboBox<>(FXCollections.observableArrayList(values));combo.setMaxWidth(Double.MAX_VALUE);if(selected!=null)values.stream().filter(item->item.id()==selected).findFirst().ifPresent(combo::setValue);else if(!values.isEmpty())combo.getSelectionModel().selectFirst();return combo;}
    private ComboBox<LookupItem> nullableCombo(List<LookupItem> values,Long selected){ComboBox<LookupItem>combo=combo(new ArrayList<>(values),selected);combo.getItems().add(0,new LookupItem(0,"— Sin asociación —"));if(selected==null)combo.getSelectionModel().selectFirst();return combo;}
    private <T> T required(ComboBox<T> combo,String label){T value=combo.getValue();if(value==null||(value instanceof LookupItem item&&item.id()==0))throw new IllegalArgumentException("Seleccione "+label);return value;}
    private Long selectedId(ComboBox<LookupItem> combo){return combo.getValue()==null||combo.getValue().id()==0?null:combo.getValue().id();}
    private String locationCode(LookupItem item){return item==null||item.id()==0?null:String.format("%06d",item.id());}
    private long userId(){return session.userId();}
    private Long id(TableRow row){return row==null?null:row.id("id");}
    private Long hiddenId(TableRow row,String key){if(row==null||row.get(key)==null)return null;return ((Number)row.get(key)).longValue();}
    private Long locationId(TableRow row,String key){if(row==null||row.get(key)==null)return null;try{return Long.parseLong(String.valueOf(row.get(key)));}catch(Exception e){return null;}}
    private String value(TableRow row,String key,String fallback){return row==null||row.get(key)==null?fallback:String.valueOf(row.get(key));}
    private javafx.util.StringConverter<TableRow> rowConverter(String...keys){return new javafx.util.StringConverter<>(){public String toString(TableRow row){if(row==null)return "";return String.join(" · ",Arrays.stream(keys).map(k->UiSupport.text(row.get(k))).toList());}public TableRow fromString(String s){return null;}};}
}
