import java.util.Locale;
public class Countries { public static void main(String[] a){ Locale he=Locale.forLanguageTag("he");
 for(String c: Locale.getISOCountries()){ Locale l=new Locale("",c); System.out.println(c+"\t"+l.getDisplayCountry(Locale.ENGLISH)+"\t"+l.getDisplayCountry(he)); } } }
