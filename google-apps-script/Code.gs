const SHEETS = {
  choirMembers: {
    name: 'Chorzysci',
    headers: [
      'Imię i nazwisko',
      'Nr ewidencyjny osoby',
      'Telefon',
      'Mail',
      'Rozmiar',
      'Głos',
      'Status'
    ]
  },
  costumes: {
    name: 'Stroje',
    headers: [
      'Kategoria stroju',
      'Numer stroju',
      'Rozmiar',
      'Status',
      'Aktualnie_Wypozyczajacy'
    ]
  },
  historyEntries: {
    name: 'Historia_Wypozyczen',
    headers: [
      'ID_Operacji',
      'Data_wypozyczenia',
      'Data_zwrotu',
      'Kategoria stroju',
      'Numer stroju',
      'Nr ewidencyjny osoby',
      'Imię i nazwisko',
      'Kaucja'
    ]
  }
};

function doGet(e) {
  try {
    const action = e && e.parameter && e.parameter.action;
    if (!action || action === 'snapshot') {
      return jsonResponse({
        ok: true,
        choirMembers: readSheet(SHEETS.choirMembers),
        costumes: readSheet(SHEETS.costumes),
        historyEntries: readSheet(SHEETS.historyEntries)
      });
    }
    return jsonResponse({ ok: false, error: 'Nieznana akcja GET' });
  } catch (error) {
    return jsonResponse({ ok: false, error: error.message });
  }
}

function doPost(e) {
  const lock = LockService.getDocumentLock();
  lock.waitLock(30000);
  try {
    const payload = JSON.parse(e.postData.contents || '{}');
    if (payload.action !== 'replaceSnapshot') {
      return jsonResponse({ ok: false, error: 'Nieznana akcja POST' });
    }

    writeSheet(SHEETS.choirMembers, payload.choirMembers || []);
    writeSheet(SHEETS.costumes, payload.costumes || []);
    writeSheet(SHEETS.historyEntries, payload.historyEntries || []);

    return jsonResponse({ ok: true });
  } catch (error) {
    return jsonResponse({ ok: false, error: error.message });
  } finally {
    lock.releaseLock();
  }
}

function readSheet(definition) {
  const sheet = getOrCreateSheet(definition);
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) {
    return [];
  }

  const values = sheet.getRange(2, 1, lastRow - 1, definition.headers.length).getDisplayValues();
  return values
    .filter(row => row.some(cell => cell !== ''))
    .map(row => {
      const object = {};
      definition.headers.forEach((header, index) => {
        object[header] = row[index] === '' ? null : row[index];
      });
      return object;
    });
}

function writeSheet(definition, rows) {
  const sheet = getOrCreateSheet(definition);
  sheet.clearContents();
  sheet.getRange(1, 1, 1, definition.headers.length).setValues([definition.headers]);

  if (!rows.length) {
    return;
  }

  const values = rows.map(row =>
    definition.headers.map(header => {
      const value = row[header];
      return value === null || value === undefined ? '' : String(value);
    })
  );
  sheet.getRange(2, 1, values.length, definition.headers.length).setValues(values);
}

function getOrCreateSheet(definition) {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = spreadsheet.getSheetByName(definition.name);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(definition.name);
  }

  const headerRange = sheet.getRange(1, 1, 1, definition.headers.length);
  const currentHeaders = headerRange.getDisplayValues()[0];
  const headersMatch = definition.headers.every((header, index) => currentHeaders[index] === header);
  if (!headersMatch) {
    headerRange.setValues([definition.headers]);
  }

  return sheet;
}

function jsonResponse(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
