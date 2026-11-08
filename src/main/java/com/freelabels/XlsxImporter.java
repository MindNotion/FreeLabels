package com.freelabels;

import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;

/** Imports arbitrary-column label sheets. Each non-empty cell becomes one ordered label line. */
public final class XlsxImporter {
    private XlsxImporter() {}
    public static List<Label> importFile(File file) throws IOException {
        List<Label> out=new ArrayList<>();
        try(FileInputStream fis=new FileInputStream(file); Workbook wb=WorkbookFactory.create(fis)){
            if(wb.getNumberOfSheets()==0) return out;
            Sheet sheet=wb.getSheetAt(0); DataFormatter fmt=new DataFormatter();
            for(Row row:sheet){
                if(row==null) continue;
                Label label=new Label();
                int last=Math.max(0,row.getLastCellNum());
                for(int c=0;c<last;c++){
                    Cell cell=row.getCell(c,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value=cell==null?"":fmt.formatCellValue(cell).trim();
                    if(value.isEmpty()) { label.customFields.add(new Label.CustomField("",Label.CustomField.Type.TEXT)); continue; }
                    Label.CustomField.Type type=Label.CustomField.Type.TEXT;
                    if(value.regionMatches(true,0,"barcode=",0,8)){
                        type=Label.CustomField.Type.BARCODE; value=value.substring(8);
                    } else if(value.regionMatches(true,0,"qr_code=",0,8)){
                        type=Label.CustomField.Type.QR; value=value.substring(8);
                    }
                    label.customFields.add(new Label.CustomField(value,type));
                }
                boolean any=false; for(Label.CustomField f:label.customFields) if(!f.value.trim().isEmpty()){any=true;break;}
                if(any) out.add(label);
            }
        } catch(org.apache.poi.EncryptedDocumentException ex) {
            throw new IOException("The Excel workbook is encrypted or invalid.",ex);
        }
        return out;
    }
}
