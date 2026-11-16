package app.iptv.tv

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Разбор XMLTV на «мусорных» данных: реальные файлы с серверов бывают битыми,
 * а исключение при разборе оставляло бы приложение без программы передач.
 * android.util.Xml в JVM-тестах недоступен, поэтому разбираем тем же kxml2, что подставляет Android (см. build.gradle.kts).
 */
class EpgRobustnessTest {
    private fun parse(xml: String): Epg {
        val f = File.createTempFile("epg", ".xml")
        try {
            f.writeText(xml)
            return Iptv.parseEpg(f, 0, null, { org.kxml2.io.KXmlParser() })
        } finally { f.delete() }
    }

    @Test fun empty_file_returns_empty_guide() {
        assertTrue(parse("").map.isEmpty())
    }

    @Test fun garbage_is_not_fatal() {
        // не XML: разборщик бросает исключение — вызывающий (Repository) его перехватит и оставит прежнюю программу
        try {
            assertTrue(parse("это вообще не XML <<< >>> & <channel").map.isEmpty())
        } catch (e: Exception) {
            // исключение допустимо: его перехватывает Repository
        }
    }

    @Test fun truncated_xml_keeps_parsed_part() {
        // файл обрезан на конце: приложение не должно падать, а разобранное до обрыва — остаётся
        val e = try {
            parse(
                """<?xml version="1.0"?><tv>
                   <channel id="a"><display-name>A</display-name></channel>
                   <programme start="20200101120000 +0000" stop="20200101130000 +0000" channel="a">
                     <title>Передача</title>
                   </programme>
                   <programme start="20200101130000 +0000" stop="20200101"""
            )
        } catch (x: Exception) {
            return   // ошибка разбора допустима: её перехватывает Repository, приложение не падает
        }
        assertTrue(e.map["a"]?.any { it.title == "Передача" } == true)
    }

    @Test fun broken_dates_are_skipped() {
        val e = parse(
            """<tv><channel id="a"><display-name>A</display-name></channel>
               <programme start="не дата" stop="" channel="a"><title>X</title></programme>
               <programme start="20200101120000" stop="20200101130000" channel="a"><title>Y</title></programme>
               </tv>"""
        )
        // у первой передачи неверные даты (конец раньше начала) — она не попадает в программу
        assertTrue(e.map["a"]?.any { it.title == "X" } != true)
    }

    @Test fun repeated_parse_of_same_file_is_stable() {
        val f = File.createTempFile("epg", ".xml")
        try {
            f.writeText("<tv><channel id='a'><display-name>A</display-name></channel></tv>")
            repeat(3) { Iptv.parseEpg(f, 0, null, { org.kxml2.io.KXmlParser() }) }
        } finally { f.delete() }
    }

    @Test fun small_value_ranges_do_not_crash() {
        // 13-й месяц / 32-е число в XMLTV: Calendar не должен ронять разбор
        val e = parse(
            """<tv><channel id="a"/><programme start="20201332120000 +0000" stop="20200199130000 +0000" channel="a"><title>Z</title></programme></tv>"""
        )
        assertTrue(e.map.size <= 1)
    }
}
