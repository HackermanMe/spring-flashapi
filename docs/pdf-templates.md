# Custom PDF Templates with JasperReports

## Quick Start

### 1. Add dependency

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>6.21.0</version>
</dependency>
```

### 2. Create template

Place `.jrxml` in `src/main/resources/flashapi/reports/`:

```
src/main/resources/flashapi/reports/
├── invoices.jrxml      ← For Invoice entity
├── products.jrxml      ← For Product entity
```

**Naming:** Lowercase entity name + `.jrxml`.

### 3. Export

```bash
GET /api/invoices/export?format=pdf
```

FlashAPI auto-detects `invoices.jrxml` and uses it.

---

## Installing Jaspersoft Studio

Download: [https://community.jaspersoft.com/project/jaspersoft-studio](https://community.jaspersoft.com/project/jaspersoft-studio)

**WYSIWYG designer** — drag fields, add logos, preview.

---

## Example Template

### Goal: Branded invoice with logo

**File:** `src/main/resources/flashapi/reports/invoices.jrxml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
              name="invoices" pageWidth="595" pageHeight="842" 
              columnWidth="555" leftMargin="20" rightMargin="20" 
              topMargin="20" bottomMargin="20">
    
    <!-- FlashAPI parameters -->
    <parameter name="ENTITY_NAME" class="java.lang.String"/>
    <parameter name="RECORD_COUNT" class="java.lang.Integer"/>
    
    <!-- Entity fields -->
    <field name="id" class="java.lang.Long"/>
    <field name="amount" class="java.math.BigDecimal"/>
    <field name="status" class="java.lang.String"/>
    <field name="createdAt" class="java.time.LocalDateTime"/>
    
    <!-- Header with logo -->
    <title>
        <band height="80">
            <image>
                <reportElement x="0" y="0" width="100" height="60"/>
                <imageExpression>
                    <![CDATA["classpath:static/logo.png"]]>
                </imageExpression>
            </image>
            
            <staticText>
                <reportElement x="200" y="20" width="200" height="30"/>
                <textElement textAlignment="Center">
                    <font size="18" isBold="true"/>
                </textElement>
                <text><![CDATA[Invoice Report]]></text>
            </staticText>
            
            <textField>
                <reportElement x="450" y="50" width="100" height="20"/>
                <textElement textAlignment="Right"/>
                <textFieldExpression>
                    <![CDATA["Total: " + $P{RECORD_COUNT}]]>
                </textFieldExpression>
            </textField>
        </band>
    </title>
    
    <!-- Column headers -->
    <columnHeader>
        <band height="30">
            <staticText>
                <reportElement x="0" y="0" width="100" height="20"/>
                <text><![CDATA[ID]]></text>
            </staticText>
            <staticText>
                <reportElement x="100" y="0" width="150" height="20"/>
                <text><![CDATA[Amount]]></text>
            </staticText>
            <staticText>
                <reportElement x="250" y="0" width="150" height="20"/>
                <text><![CDATA[Status]]></text>
            </staticText>
            <staticText>
                <reportElement x="400" y="0" width="155" height="20"/>
                <text><![CDATA[Created]]></text>
            </staticText>
        </band>
    </columnHeader>
    
    <!-- Data rows -->
    <detail>
        <band height="25">
            <textField>
                <reportElement x="0" y="0" width="100" height="20"/>
                <textFieldExpression><![CDATA[$F{id}]]></textFieldExpression>
            </textField>
            <textField pattern="#,##0.00">
                <reportElement x="100" y="0" width="150" height="20"/>
                <textFieldExpression><![CDATA[$F{amount}]]></textFieldExpression>
            </textField>
            <textField>
                <reportElement x="250" y="0" width="150" height="20"/>
                <textFieldExpression><![CDATA[$F{status}]]></textFieldExpression>
            </textField>
            <textField pattern="yyyy-MM-dd HH:mm">
                <reportElement x="400" y="0" width="155" height="20"/>
                <textFieldExpression>
                    <![CDATA[$F{createdAt} != null ? 
                        java.sql.Timestamp.valueOf($F{createdAt}) : ""]]>
                </textFieldExpression>
            </textField>
        </band>
    </detail>
    
    <!-- Footer -->
    <pageFooter>
        <band height="30">
            <textField>
                <reportElement x="200" y="10" width="150" height="20"/>
                <textElement textAlignment="Center"/>
                <textFieldExpression>
                    <![CDATA["Page " + $V{PAGE_NUMBER}]]>
                </textFieldExpression>
            </textField>
        </band>
    </pageFooter>
    
</jasperReport>
```

Place logo at `src/main/resources/static/logo.png`.

---

## Available Variables

| Parameter | Type | Description |
|-----------|------|-------------|
| `ENTITY_NAME` | `String` | Entity name |
| `RECORD_COUNT` | `Integer` | Total records |

**Usage:**
```xml
<textFieldExpression>
    <![CDATA[$P{ENTITY_NAME}]]>
</textFieldExpression>
```

---

## Field Mapping

| Java Type | JasperReports Type |
|-----------|-------------------|
| `Long`, `Integer` | `java.lang.Long`, `java.lang.Integer` |
| `String` | `java.lang.String` |
| `BigDecimal` | `java.math.BigDecimal` |
| `LocalDateTime` | `java.time.LocalDateTime` |
| `LocalDate` | `java.time.LocalDate` |
| `Boolean` | `java.lang.Boolean` |

**Access:** `$F{fieldName}`

---

## Filters Apply

All query params work with templates:

```bash
GET /api/invoices/export?format=pdf&status=paid&amount.gte=1000&sort=createdAt,desc
```

FlashAPI applies filters before feeding data to your template.

---

## Configuration

```yaml
flashapi:
  export:
    max-rows: 0  # 0 = unlimited
    reports-path: flashapi/reports
```

---

## Troubleshooting

### ❌ Template not found

**Fix:** Check naming — `Invoice` → `invoices.jrxml` (lowercase).

### ❌ Field not found

**Fix:** Use exact entity field names. For nested fields (`author.name`), add a `@Transient` getter:

```java
@Transient
public String getAuthorName() {
    return author != null ? author.getName() : "";
}
```

Use `$F{authorName}` in template.

### ❌ PDF is blank

**Fix:** Set `<detail>` band height > 0:

```xml
<detail>
    <band height="25">  <!-- Must be > 0 -->
```

---

## Default Behavior

No `.jrxml`? FlashAPI generates a clean table layout automatically.

**Use custom template for:** Branded PDFs, customer-facing documents.

---

## Next Steps

- **[Export Guide](export.md)** — CSV/Excel/PDF
- **[Best Practices](best-practices.md)** — patterns
- **[Configuration](configuration.md)** — all settings

---

## Resources

- **JasperReports:** [https://jasperreports.sourceforge.net/](https://jasperreports.sourceforge.net/)
- **Jaspersoft Studio:** [https://community.jaspersoft.com/project/jaspersoft-studio](https://community.jaspersoft.com/project/jaspersoft-studio)
