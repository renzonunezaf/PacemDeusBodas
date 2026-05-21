package com.pacemdeus.bodas.data

// Archivo obsoleto.
//
// Originalmente proveia datos hardcoded para mostrar pantallas durante
// el TB1 antes de tener backend. Desde el Sprint 2 todas las pantallas
// leen del backend AWS via ApiClient, por lo que este archivo dejo de
// usarse en runtime.
//
// Lo conservamos vacio en lugar de eliminarlo porque al descomprimir un
// ZIP sobre el filesystem no se borran archivos preexistentes. Si se
// borrara, el viejo DemoData con las firmas antiguas de Instrument/Song
// rompe la compilacion en cada update.
//
// Si en el futuro se necesita un dataset para pruebas locales, agregar
// aqui constructores que respeten las firmas actuales:
//   - Instrument(id: String, name: String, sortOrder: Int, includedInBasePackage: Boolean = false)
//     donde id = slug
//   - Song(id: String, title: String, author: String, language: String)
//
// No agregar logica de negocio aqui. Si necesitas fixtures, mejor crear
// un archivo en test/ o crear una pantalla developer-only.

object DemoData {
    /** Telefono del coro al que el planner llama desde el detalle. */
    const val CHOIR_PHONE = "+51989159777"
}
