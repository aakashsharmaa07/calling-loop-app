package com.aakash.callloop.data

data class Country(
    val name: String,
    val dialCode: String,
    val flagEmoji: String,
    val code: String,
    val minDigits: Int = 10,
    val maxDigits: Int = 10
)

object CountryData {
    val defaultCountry = Country(
        name = "India",
        dialCode = "+91",
        flagEmoji = "🇮🇳",
        code = "IN",
        minDigits = 10,
        maxDigits = 10
    )

    val countries = listOf(
        Country("India", "+91", "🇮🇳", "IN", 10, 10),
        Country("United States", "+1", "🇺🇸", "US", 10, 10),
        Country("United Kingdom", "+44", "🇬🇧", "GB", 10, 10),
        Country("United Arab Emirates", "+971", "🇦🇪", "AE", 9, 9),
        Country("Canada", "+1", "🇨🇦", "CA", 10, 10),
        Country("Australia", "+61", "🇦🇺", "AU", 9, 9),
        Country("Singapore", "+65", "🇸🇬", "SG", 8, 8),
        Country("Saudi Arabia", "+966", "🇸🇦", "SA", 9, 9),
        Country("Germany", "+49", "🇩🇪", "DE", 10, 11),
        Country("France", "+33", "🇫🇷", "FR", 9, 9),
        Country("Nepal", "+977", "🇳🇵", "NP", 10, 10),
        Country("Bangladesh", "+880", "🇧🇩", "BD", 10, 10),
        Country("Pakistan", "+92", "🇵🇰", "PK", 10, 10),
        Country("Sri Lanka", "+94", "🇱🇰", "LK", 9, 9),
        Country("Malaysia", "+60", "🇲🇾", "MY", 9, 10),
        Country("Kuwait", "+965", "🇰🇼", "KW", 8, 8),
        Country("Qatar", "+974", "🇶🇦", "QA", 8, 8),
        Country("Oman", "+968", "🇴🇲", "OM", 8, 8),
        Country("Bahrain", "+973", "🇧🇭", "BH", 8, 8),
        Country("Japan", "+81", "🇯🇵", "JP", 10, 10),
        Country("South Korea", "+82", "🇰🇷", "KR", 9, 10),
        Country("China", "+86", "🇨🇳", "CN", 11, 11),
        Country("New Zealand", "+64", "🇳🇿", "NZ", 9, 10),
        Country("South Africa", "+27", "🇿🇦", "ZA", 9, 9),
        Country("Russia", "+7", "🇷🇺", "RU", 10, 10),
        Country("Italy", "+39", "🇮🇹", "IT", 9, 10),
        Country("Spain", "+34", "🇪🇸", "ES", 9, 9),
        Country("Netherlands", "+31", "🇳🇱", "NL", 9, 9),
        Country("Switzerland", "+41", "🇨🇭", "CH", 9, 9),
        Country("Sweden", "+46", "🇸🇪", "SE", 9, 10),
        Country("Norway", "+47", "🇳🇴", "NO", 8, 8),
        Country("Ireland", "+353", "🇮🇪", "IE", 9, 9),
        Country("Brazil", "+55", "🇧🇷", "BR", 10, 11),
        Country("Indonesia", "+62", "🇮🇩", "ID", 9, 12),
        Country("Philippines", "+63", "🇵🇭", "PH", 10, 10),
        Country("Thailand", "+66", "🇹🇭", "TH", 9, 9),
        Country("Vietnam", "+84", "🇻🇳", "VN", 9, 10),
        Country("Turkey", "+90", "🇹🇷", "TR", 10, 10),
        Country("Egypt", "+20", "🇪🇬", "EG", 10, 10),
        Country("Nigeria", "+234", "🇳🇬", "NG", 10, 10),
        Country("Kenya", "+254", "🇰🇪", "KE", 9, 9),
        Country("Mexico", "+52", "🇲🇽", "MX", 10, 10)
    )

    fun findByDialCode(dialCode: String): Country? {
        val cleanCode = if (dialCode.startsWith("+")) dialCode else "+$dialCode"
        return countries.firstOrNull { it.dialCode == cleanCode }
    }

    fun findByCode(countryCode: String): Country? {
        return countries.firstOrNull { it.code.equals(countryCode, ignoreCase = true) }
    }
}
