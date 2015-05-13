package jhotfix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class JHotFix {

//	// Dummy
//	public static boolean downloadFromUrl2(URL url, String localFilename) throws IOException {
//		return true;
//	}
	
	public static boolean downloadFromUrl(URL url, String localFilename) throws IOException, ParseException {
		
		InputStream is = null;
		FileOutputStream fos = null;
        int contentLength=0, contentGot=0;
		Date lastModified = null;
        
		try {
			URLConnection urlConn = url.openConnection();
			// Credenziali di IE 11
			urlConn.setRequestProperty("User-Agent", "Mozilla / 5.0 (Windows NT 6.3; Trident / 7.0; rv:11.0) like Gecko");

			is = urlConn.getInputStream();
			
			Map<String, List<String>> keys = urlConn.getHeaderFields();
			
			if (keys.containsKey("Last-Modified")) {
				String s = keys.get("Last-Modified").get(0);
				try {
					DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
					lastModified = df.parse(s);
				} catch (Exception e) {
					
				}
			}
			
			// Questa chiave c'è sempre?
//			if (keys.containsKey("Content-Length")) {
			contentLength = Integer.parseInt(keys.get("Content-Length").get(0));
//			}

			fos = new FileOutputStream(localFilename);
			
			byte[] buffer = new byte[65536];
			int len;

			while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
                contentGot += len;
                
                // Stampa l'avanzamento
				String s = String.format("%.2f/%.2f MiB",
						contentGot/(1.0*(1<<20)),
						contentLength/(1.0*(1<<20)));
				char[] f = new char[s.length()];
				Arrays.fill(f, '\b');
				System.out.print(s + new String(f));
				
				// Al termine, cancella il messaggio
				if (contentGot == contentLength) {
					char[] g = new char[s.length()];
					Arrays.fill(g, ' ');
					System.out.print(new String(g) + new String(f));
				}
            }
        } finally {
        	try {
        		if (is != null) {
        			is.close();
        		}
        	} finally {
        		if (fos != null) {
        			fos.close();
        			// Se conosciamo la lunghezza del file, ma non l'abbiamo raggiunta...
        			if (contentLength != 0 && contentLength != contentGot) {
        				System.out.println("errore, file incompleto!");
        				new File(localFilename).delete();
        				return false;
        			}
        			
        			// Se il download è andato a buon fine, e abbiamo
        			// la data di ultima modifica, la applica
        			if (lastModified != null)
        				new File(localFilename).setLastModified(lastModified.getTime());
        		} else
        			return false;
        	}
        }
		
		return true;
	}


	public static void main(String[] args) throws IOException, ParseException {
		
		// Query di ricerca dal sito Microsoft Update (restituisce 10 risultati alla volta)
		String windows_query = "http://www.microsoft.com/it-it/search/DownloadResults.aspx?q=windows+8.1&sortby=-availabledate&First=%d&ftapplicableproducts=AllDownloads";
		String office_query = "http://www.microsoft.com/it-it/search/DownloadResults.aspx?q=office+2013&First=%d&ftapplicableproducts=Office&sortby=-availabledate";
		String query = windows_query;
		Date data_limite = null;
		
		// Modelli di descrizione di Hotfix per Windows 8.1
		// title="Aggiornamento per Windows 8.1 (KB2938066)" href="http://www.microsoft.com/it-it/download/details.aspx?id=43468"
		// Aggiornamento della protezione per Windows 8.1 (KB2978742)
		// Pacchetto cumulativo di aggiornamenti della sicurezza per Internet Explorer 11 per Windows 8.1 x64 (KB2976627)
		// 	
		// !!! ATTENZIONE !!! PERIODICAMENTE PUO' ESSERE MODIFICATO!
		Pattern windows_rx = Pattern.compile("(Aggiornamento .+?Windows 8.1.+?KB\\d+\\)|Pacchetto.+?Explorer 11 per Windows 8.1.+?KB\\d+\\)|Aggiornamento.+?Server 2012 R2)");
		Pattern office_rx = Pattern.compile("(Aggiornamento .+?KB\\d+\\) Edizione a 32 bit)");
		Pattern pattern = windows_rx;
		
		// Argomenti:
		// -windows		implicito
		// -office 		scarica gli aggiornamenti per Office 2013
		// gg/mm/aaaa 	non scarica pacchetti più vecchi della data specificata
		if (args.length == 0) {
			System.out.println("Usa: JHotFix -windows|-office [gg/mm/aaaa]");
			return;
		}
		
		if ("-office".equals(args[0])) {
			query = office_query;
			pattern = office_rx;
		}
		
		if (args.length == 2) {
			DateFormat df = new SimpleDateFormat("dd/MM/yyyy");
			data_limite = df.parse(args[1]);
		}

		// Se non c'è ancora una data limite, la fissa a 3 settimane fa
		if (data_limite == null)
			data_limite = new Date(new Date().getTime()-1814400000);
	
		int curPage = 1;
		int downloads = 0;
		int bytes_downloaded = 0;
		Date start = new Date();
Main_Loop:
		while (true) {
			String url = String.format(query, curPage);
			curPage += 10;
			System.out.println("Interrogo "+url);
			
			// Query CSS per selezionare ciascun gruppo di celle contenenti un singolo risultato
			// (ricavato mediante gli strumenti di sviluppo-analisi pagina di Firefox 37)
			String select_results = ".download_results > div:nth-child(1) > div:nth-child(%d) > div:nth-child(1) > div:nth-child(1)";
			Document doc = Jsoup.connect(url).timeout(3000).get();
			
			// Esamina ciascuno dei 10 risultati
			for (int i=1; i < 11; i++) {
				String select_result = String.format(select_results, i);
				
				// Query CSS per selezionare la data dell'aggiornamento
				Elements data = doc.select(select_result + " > div:nth-child(5) > div:nth-child(1) > div:nth-child(1) > div:nth-child(4) > div:nth-child(1)");
				DateFormat df = new SimpleDateFormat("dd/MM/yyyy");
				Date d = df.parse(data.html());
				// Se la data precede il limite fissato, termina
				if (d.before(data_limite))
					break Main_Loop;
				
				// Query CSS per selezionare il gruppo <a></a> dell'aggiornamento
				Elements anchor = doc.select(select_result + " > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > a:nth-child(1)");

				// Se il titolo dell'aggiornamento non ci soddisfa, continua
				if ( !pattern.matcher(anchor.attr("title")).matches())
					continue;
				
				// Esamina la pagina di conferma NON TRASFORMATA da JavaScript				
				Document doc2 = Jsoup.connect(anchor.attr("href")
						.replace("details.aspx", "confirmation.aspx"))
						.timeout(5000).get();
				
				// Recupera il link al pacchetto
				URL url3 = new URL(doc2.select("span > a").attr("href"));
				
				String f = new File(url3.getHost() + url3.getPath()).getName();
				// Evita l'architettura ARM
				if (f.contains("arm"))
					continue;
				System.out.print("Download di " + f + "... ");
				// Se esiste, continua 
				if (new File(f).exists()) {
					System.out.println("no, presente.");
					continue;
				}
				// Inserire un loop per tentare 2-3 volte?
				try {
					downloadFromUrl(url3, f);
				}
				catch (IOException e) {
					System.out.println("fallito!");
					continue;
				}
				System.out.println("ok.");
				downloads++;
				bytes_downloaded += new File(f).length();
			}
		}
		Date stop = new Date();
		long x = (stop.getTime()-start.getTime())/1000;
		System.out.println(String.format("Scaricati %d file (%d byte) in %d secondi @%.3f KiB/s.",
				downloads, bytes_downloaded, x, bytes_downloaded/1024.0/x));
	}

}
