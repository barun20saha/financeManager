package com.example.myaccounts.storage

import com.example.myaccounts.model.*
import org.bson.*
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer

object BsonStorage {

    fun appDataToBson(appData: AppData): ByteArray {
        val root = BsonDocument()

        // 1. Customers
        val custArray = BsonArray()
        appData.customers.forEach { c ->
            val doc = BsonDocument().apply {
                put("id", BsonString(c.id))
                put("name", BsonString(c.name))
                put("color", BsonString(c.color))
            }
            custArray.add(doc)
        }
        root.put("customers", custArray)

        // 2. Suppliers
        val suppArray = BsonArray()
        appData.suppliers.forEach { s ->
            val doc = BsonDocument().apply {
                put("id", BsonString(s.id))
                put("name", BsonString(s.name))
                put("color", BsonString(s.color))
            }
            suppArray.add(doc)
        }
        root.put("suppliers", suppArray)

        // 3. Transactions map: entityId -> List<Transaction>
        val txMapDoc = BsonDocument()
        appData.transactions.forEach { (entityId, txList) ->
            val txArray = BsonArray()
            txList.forEach { tx ->
                val txDoc = BsonDocument().apply {
                    put("id", BsonString(tx.id))
                    put("createdAt", BsonInt64(tx.createdAt))
                    put("type", BsonString(tx.type))
                    put("amount", BsonDouble(tx.amount))
                    put("note", BsonString(tx.note))
                    put("date", BsonString(tx.date))

                    val stArray = BsonArray()
                    tx.settlements.forEach { st ->
                        val stDoc = BsonDocument().apply {
                            put("amount", BsonDouble(st.amount))
                            put("date", BsonString(st.date))
                            put("note", BsonString(st.note))
                        }
                        stArray.add(stDoc)
                    }
                    put("settlements", stArray)
                }
                txArray.add(txDoc)
            }
            txMapDoc.put(entityId, txArray)
        }
        root.put("transactions", txMapDoc)

        // 4. Trips
        val tripArray = BsonArray()
        appData.trips.forEach { trip ->
            val tDoc = BsonDocument().apply {
                put("id", BsonString(trip.id))
                put("name", BsonString(trip.name))
                put("createdAt", BsonInt64(trip.createdAt))

                val memArray = BsonArray()
                trip.members.forEach { memArray.add(BsonString(it)) }
                put("members", memArray)

                val expArray = BsonArray()
                trip.expenses.forEach { exp ->
                    val expDoc = BsonDocument().apply {
                        put("id", BsonString(exp.id))
                        put("description", BsonString(exp.description))
                        put("amount", BsonDouble(exp.amount))
                        put("paidBy", BsonString(exp.paidBy))

                        val partArray = BsonArray()
                        exp.participants.forEach { partArray.add(BsonString(it)) }
                        put("participants", partArray)

                        put("splitType", BsonString(exp.splitType))

                        val sharesDoc = BsonDocument()
                        exp.shares.forEach { (m, a) ->
                            sharesDoc.put(m, BsonDouble(a))
                        }
                        put("shares", sharesDoc)

                        put("date", BsonString(exp.date))
                        put("createdAt", BsonInt64(exp.createdAt))
                    }
                    expArray.add(expDoc)
                }
                put("expenses", expArray)
            }
            tripArray.add(tDoc)
        }
        root.put("trips", tripArray)

        // 5. Logs
        val logArray = BsonArray()
        appData.logs.forEach { log ->
            val lDoc = BsonDocument().apply {
                put("id", BsonString(log.id))
                put("type", BsonString(log.type))
                put("detail", BsonString(log.detail))
                put("status", BsonString(log.status))
                put("timestamp", BsonInt64(log.timestamp))
                put("device", BsonString(log.device))
            }
            logArray.add(lDoc)
        }
        root.put("logs", logArray)

        // 6. Settings
        val setDoc = BsonDocument().apply {
            put("appLockEnabled", BsonBoolean(appData.settings.appLockEnabled))
            put("biometricsEnabled", BsonBoolean(appData.settings.biometricsEnabled))
            if (appData.settings.pinSalt != null) put("pinSalt", BsonString(appData.settings.pinSalt))
            if (appData.settings.pinHash != null) put("pinHash", BsonString(appData.settings.pinHash))
        }
        root.put("settings", setDoc)

        val outputBuffer = BasicOutputBuffer()
        val writer = BsonBinaryWriter(outputBuffer)
        BsonDocumentCodec().encode(writer, root, EncoderContext.builder().build())
        return outputBuffer.toByteArray()
    }

    fun bsonToAppData(bytes: ByteArray): AppData {
        val reader = BsonBinaryReader(ByteBuffer.wrap(bytes))
        val root = BsonDocumentCodec().decode(reader, DecoderContext.builder().build())

        // 1. Customers
        val customers = mutableListOf<Customer>()
        root.getArray("customers", BsonArray()).forEach {
            if (it is BsonDocument) {
                customers.add(
                    Customer(
                        id = it.getString("id", BsonString("")).value,
                        name = it.getString("name", BsonString("")).value,
                        color = it.getString("color", BsonString("#065f46")).value
                    )
                )
            }
        }

        // 2. Suppliers
        val suppliers = mutableListOf<Supplier>()
        root.getArray("suppliers", BsonArray()).forEach {
            if (it is BsonDocument) {
                suppliers.add(
                    Supplier(
                        id = it.getString("id", BsonString("")).value,
                        name = it.getString("name", BsonString("")).value,
                        color = it.getString("color", BsonString("#065f46")).value
                    )
                )
            }
        }

        // 3. Transactions
        val transactions = mutableMapOf<String, List<Transaction>>()
        val txMapDoc = root.getDocument("transactions", BsonDocument())
        txMapDoc.keys.forEach { entityId ->
            val list = mutableListOf<Transaction>()
            val txArr = txMapDoc.getArray(entityId, BsonArray())
            txArr.forEach { item ->
                if (item is BsonDocument) {
                    val stList = mutableListOf<Settlement>()
                    item.getArray("settlements", BsonArray()).forEach { stItem ->
                        if (stItem is BsonDocument) {
                            stList.add(
                                Settlement(
                                    amount = stItem.getDouble("amount", BsonDouble(0.0)).value,
                                    date = stItem.getString("date", BsonString("")).value,
                                    note = stItem.getString("note", BsonString("")).value
                                )
                            )
                        }
                    }
                    list.add(
                        Transaction(
                            id = item.getString("id", BsonString("")).value,
                            createdAt = item.getInt64("createdAt", BsonInt64(0)).value,
                            type = item.getString("type", BsonString("debit")).value,
                            amount = item.getDouble("amount", BsonDouble(0.0)).value,
                            note = item.getString("note", BsonString("")).value,
                            date = item.getString("date", BsonString("")).value,
                            settlements = stList
                        )
                    )
                }
            }
            transactions[entityId] = list
        }

        // 4. Trips
        val trips = mutableListOf<Trip>()
        root.getArray("trips", BsonArray()).forEach { tItem ->
            if (tItem is BsonDocument) {
                val memList = mutableListOf<String>()
                tItem.getArray("members", BsonArray()).forEach { m ->
                    if (m is BsonString) memList.add(m.value)
                }

                val expList = mutableListOf<TripExpense>()
                tItem.getArray("expenses", BsonArray()).forEach { eItem ->
                    if (eItem is BsonDocument) {
                        val partList = mutableListOf<String>()
                        eItem.getArray("participants", BsonArray()).forEach { p ->
                            if (p is BsonString) partList.add(p.value)
                        }

                        val sharesMap = mutableMapOf<String, Double>()
                        val sDoc = eItem.getDocument("shares", BsonDocument())
                        sDoc.keys.forEach { k ->
                            sharesMap[k] = sDoc.getDouble(k, BsonDouble(0.0)).value
                        }

                        expList.add(
                            TripExpense(
                                id = eItem.getString("id", BsonString("")).value,
                                description = eItem.getString("description", BsonString("")).value,
                                amount = eItem.getDouble("amount", BsonDouble(0.0)).value,
                                paidBy = eItem.getString("paidBy", BsonString("")).value,
                                participants = partList,
                                splitType = eItem.getString("splitType", BsonString("equal")).value,
                                shares = sharesMap,
                                date = eItem.getString("date", BsonString("")).value,
                                createdAt = eItem.getInt64("createdAt", BsonInt64(0)).value
                            )
                        )
                    }
                }

                trips.add(
                    Trip(
                        id = tItem.getString("id", BsonString("")).value,
                        name = tItem.getString("name", BsonString("")).value,
                        createdAt = tItem.getInt64("createdAt", BsonInt64(0)).value,
                        members = if (memList.isEmpty()) listOf("Me") else memList,
                        expenses = expList
                    )
                )
            }
        }

        // 5. Logs
        val logs = mutableListOf<ActivityLog>()
        root.getArray("logs", BsonArray()).forEach { lItem ->
            if (lItem is BsonDocument) {
                logs.add(
                    ActivityLog(
                        id = lItem.getString("id", BsonString("")).value,
                        type = lItem.getString("type", BsonString("")).value,
                        detail = lItem.getString("detail", BsonString("")).value,
                        status = lItem.getString("status", BsonString("success")).value,
                        timestamp = lItem.getInt64("timestamp", BsonInt64(0)).value,
                        device = lItem.getString("device", BsonString("Android Device")).value
                    )
                )
            }
        }

        // 6. Settings
        val setDoc = root.getDocument("settings", BsonDocument())
        val settings = SecuritySettings(
            appLockEnabled = setDoc.getBoolean("appLockEnabled", BsonBoolean(false)).value,
            biometricsEnabled = setDoc.getBoolean("biometricsEnabled", BsonBoolean(false)).value,
            pinSalt = if (setDoc.containsKey("pinSalt")) setDoc.getString("pinSalt").value else null,
            pinHash = if (setDoc.containsKey("pinHash")) setDoc.getString("pinHash").value else null
        )

        return AppData(
            customers = customers,
            suppliers = suppliers,
            transactions = transactions,
            trips = trips,
            logs = logs,
            settings = settings
        )
    }

    fun jsonToAppData(jsonStr: String): AppData {
        val json = JSONObject(jsonStr)

        val customers = mutableListOf<Customer>()
        val custArr = json.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until custArr.length()) {
            val obj = custArr.getJSONObject(i)
            customers.add(
                Customer(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    color = obj.optString("color", "#065f46")
                )
            )
        }

        val suppliers = mutableListOf<Supplier>()
        val suppArr = json.optJSONArray("suppliers") ?: JSONArray()
        for (i in 0 until suppArr.length()) {
            val obj = suppArr.getJSONObject(i)
            suppliers.add(
                Supplier(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    color = obj.optString("color", "#065f46")
                )
            )
        }

        val transactions = mutableMapOf<String, List<Transaction>>()
        val txObj = json.optJSONObject("transactions") ?: JSONObject()
        val txKeys = txObj.keys()
        while (txKeys.hasNext()) {
            val k = txKeys.next()
            val arr = txObj.optJSONArray(k) ?: JSONArray()
            val list = mutableListOf<Transaction>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val settlements = mutableListOf<Settlement>()
                val stArr = item.optJSONArray("settlements") ?: JSONArray()
                for (j in 0 until stArr.length()) {
                    val st = stArr.getJSONObject(j)
                    settlements.add(
                        Settlement(
                            amount = st.optDouble("amount", 0.0),
                            date = st.optString("date", ""),
                            note = st.optString("note", "")
                        )
                    )
                }
                list.add(
                    Transaction(
                        id = item.optString("id"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        type = item.optString("type", "debit"),
                        amount = item.optDouble("amount", 0.0),
                        note = item.optString("note", ""),
                        date = item.optString("date", ""),
                        settlements = settlements
                    )
                )
            }
            transactions[k] = list
        }

        val trips = mutableListOf<Trip>()
        val tripArr = json.optJSONArray("trips") ?: JSONArray()
        for (i in 0 until tripArr.length()) {
            val tObj = tripArr.getJSONObject(i)
            val memArr = tObj.optJSONArray("members") ?: JSONArray()
            val members = mutableListOf<String>()
            for (m in 0 until memArr.length()) {
                members.add(memArr.getString(m))
            }

            val expArr = tObj.optJSONArray("expenses") ?: JSONArray()
            val expenses = mutableListOf<TripExpense>()
            for (e in 0 until expArr.length()) {
                val eObj = expArr.getJSONObject(e)
                val partArr = eObj.optJSONArray("participants") ?: JSONArray()
                val participants = mutableListOf<String>()
                for (p in 0 until partArr.length()) {
                    participants.add(partArr.getString(p))
                }

                val sharesObj = eObj.optJSONObject("shares") ?: JSONObject()
                val shares = mutableMapOf<String, Double>()
                val sKeys = sharesObj.keys()
                while (sKeys.hasNext()) {
                    val sk = sKeys.next()
                    shares[sk] = sharesObj.optDouble(sk, 0.0)
                }

                expenses.add(
                    TripExpense(
                        id = eObj.optString("id"),
                        description = eObj.optString("description"),
                        amount = eObj.optDouble("amount", 0.0),
                        paidBy = eObj.optString("paidBy"),
                        participants = participants,
                        splitType = eObj.optString("splitType", "equal"),
                        shares = shares,
                        date = eObj.optString("date"),
                        createdAt = eObj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            trips.add(
                Trip(
                    id = tObj.optString("id"),
                    name = tObj.optString("name"),
                    createdAt = tObj.optLong("createdAt", System.currentTimeMillis()),
                    members = if (members.isEmpty()) listOf("Me") else members,
                    expenses = expenses
                )
            )
        }

        val logs = mutableListOf<ActivityLog>()
        val logArr = json.optJSONArray("logs") ?: JSONArray()
        for (i in 0 until logArr.length()) {
            val lObj = logArr.getJSONObject(i)
            logs.add(
                ActivityLog(
                    id = lObj.optString("id"),
                    type = lObj.optString("type"),
                    detail = lObj.optString("detail"),
                    status = lObj.optString("status", "success"),
                    timestamp = lObj.optLong("timestamp", System.currentTimeMillis()),
                    device = lObj.optString("device", "Android Device")
                )
            )
        }

        val setObj = json.optJSONObject("settings") ?: JSONObject()
        val settings = SecuritySettings(
            appLockEnabled = setObj.optBoolean("appLockEnabled", false),
            biometricsEnabled = setObj.optBoolean("biometricsEnabled", false),
            pinSalt = if (setObj.has("pinSalt")) setObj.optString("pinSalt") else null,
            pinHash = if (setObj.has("pinHash")) setObj.optString("pinHash") else null
        )

        return AppData(
            customers = customers,
            suppliers = suppliers,
            transactions = transactions,
            trips = trips,
            logs = logs,
            settings = settings
        )
    }

    fun appDataToJson(appData: AppData): String {
        val root = JSONObject()

        val custArr = JSONArray()
        appData.customers.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("color", c.color)
            }
            custArr.put(obj)
        }
        root.put("customers", custArr)

        val suppArr = JSONArray()
        appData.suppliers.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("color", s.color)
            }
            suppArr.put(obj)
        }
        root.put("suppliers", suppArr)

        val txObj = JSONObject()
        appData.transactions.forEach { (entityId, txList) ->
            val arr = JSONArray()
            txList.forEach { tx ->
                val item = JSONObject().apply {
                    put("id", tx.id)
                    put("createdAt", tx.createdAt)
                    put("type", tx.type)
                    put("amount", tx.amount)
                    put("note", tx.note)
                    put("date", tx.date)

                    val stArr = JSONArray()
                    tx.settlements.forEach { st ->
                        val stObj = JSONObject().apply {
                            put("amount", st.amount)
                            put("date", st.date)
                            put("note", st.note)
                        }
                        stArr.put(stObj)
                    }
                    put("settlements", stArr)
                }
                arr.put(item)
            }
            txObj.put(entityId, arr)
        }
        root.put("transactions", txObj)

        val tripArr = JSONArray()
        appData.trips.forEach { trip ->
            val tObj = JSONObject().apply {
                put("id", trip.id)
                put("name", trip.name)
                put("createdAt", trip.createdAt)

                val mArr = JSONArray()
                trip.members.forEach { mArr.put(it) }
                put("members", mArr)

                val expArr = JSONArray()
                trip.expenses.forEach { exp ->
                    val eObj = JSONObject().apply {
                        put("id", exp.id)
                        put("description", exp.description)
                        put("amount", exp.amount)
                        put("paidBy", exp.paidBy)

                        val pArr = JSONArray()
                        exp.participants.forEach { pArr.put(it) }
                        put("participants", pArr)

                        put("splitType", exp.splitType)

                        val sObj = JSONObject()
                        exp.shares.forEach { (k, v) -> sObj.put(k, v) }
                        put("shares", sObj)

                        put("date", exp.date)
                        put("createdAt", exp.createdAt)
                    }
                    expArr.put(eObj)
                }
                put("expenses", expArr)
            }
            tripArr.put(tObj)
        }
        root.put("trips", tripArr)

        val logArr = JSONArray()
        appData.logs.forEach { log ->
            val lObj = JSONObject().apply {
                put("id", log.id)
                put("type", log.type)
                put("detail", log.detail)
                put("status", log.status)
                put("timestamp", log.timestamp)
                put("device", log.device)
            }
            logArr.put(lObj)
        }
        root.put("logs", logArr)

        val setObj = JSONObject().apply {
            put("appLockEnabled", appData.settings.appLockEnabled)
            put("biometricsEnabled", appData.settings.biometricsEnabled)
            if (appData.settings.pinSalt != null) put("pinSalt", appData.settings.pinSalt)
            if (appData.settings.pinHash != null) put("pinHash", appData.settings.pinHash)
        }
        root.put("settings", setObj)

        return root.toString()
    }
}
