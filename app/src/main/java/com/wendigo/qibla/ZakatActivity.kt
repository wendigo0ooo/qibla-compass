package com.wendigo.qibla

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class ZakatActivity : AppCompatActivity() {

    private val tr = Locale("tr", "TR")

    private lateinit var fNakit: EditText
    private lateinit var fAltinG: EditText
    private lateinit var fGumusG: EditText
    private lateinit var fDiger: EditText
    private lateinit var fBorc: EditText
    private lateinit var fAltinFiyat: EditText
    private lateinit var fGumusFiyat: EditText
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zakat)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.zakatToolbar).apply {
            title = getString(R.string.tool_zakat_title)
            setNavigationOnClickListener { finish() }
        }

        fNakit = findViewById(R.id.zNakit)
        fAltinG = findViewById(R.id.zAltinG)
        fGumusG = findViewById(R.id.zGumusG)
        fDiger = findViewById(R.id.zDiger)
        fBorc = findViewById(R.id.zBorc)
        fAltinFiyat = findViewById(R.id.zAltinFiyat)
        fGumusFiyat = findViewById(R.id.zGumusFiyat)
        result = findViewById(R.id.zResult)

        findViewById<Button>(R.id.zCalc).setOnClickListener { calculate() }
    }

    private fun num(e: EditText): Double =
        e.text.toString().trim().replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun money(v: Double): String = String.format(tr, "%,.2f ₺", v)

    private fun calculate() {
        val nakit = num(fNakit)
        val altinG = num(fAltinG)
        val gumusG = num(fGumusG)
        val diger = num(fDiger)
        val borc = num(fBorc)
        val altinFiyat = num(fAltinFiyat)
        val gumusFiyat = num(fGumusFiyat)

        val altinDeger = altinG * altinFiyat
        val gumusDeger = gumusG * gumusFiyat
        val toplam = nakit + altinDeger + gumusDeger + diger
        val net = toplam - borc

        val nisabAltin = 80.18 * altinFiyat
        val nisabGumus = 595.0 * gumusFiyat

        val sb = StringBuilder()
        sb.append(getString(R.string.zk_total)).append(": ").append(money(toplam)).append('\n')
        sb.append(getString(R.string.zk_debt)).append(": ").append(money(borc)).append('\n')
        sb.append(getString(R.string.zk_net)).append(": ").append(money(net)).append("\n\n")
        if (altinFiyat > 0) sb.append(getString(R.string.zk_nisab_gold)).append(": ")
            .append(money(nisabAltin)).append('\n')
        if (gumusFiyat > 0) sb.append(getString(R.string.zk_nisab_silver)).append(": ")
            .append(money(nisabGumus)).append('\n')
        sb.append('\n')

        val nisab = when {
            altinFiyat > 0 -> nisabAltin
            gumusFiyat > 0 -> nisabGumus
            else -> 0.0
        }

        if (nisab <= 0.0) {
            sb.append(getString(R.string.zk_need_prices))
        } else if (net >= nisab) {
            val zakat = net * 0.025
            sb.append(getString(R.string.zk_due)).append(": ").append(money(zakat))
        } else {
            sb.append(getString(R.string.zk_below))
        }

        result.text = sb.toString()
    }
}
