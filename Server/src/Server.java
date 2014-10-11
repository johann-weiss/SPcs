import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

class Server extends Thread
{
    Socket s;
    public String PATH = "";

    public static void main(String args[])
    {
        try
        {

            ServerSocket server = new ServerSocket(8080, 0,
                    InetAddress.getByName("localhost"));		 // сокет на локалхост, порт 8080

            System.out.println("server is started");


            while(true)											  // слушаем порт
            {
                new Server(server.accept());			// ждём нового подключения, после чего запускаем обработку клиента в новый вычислительный поток

            }
        }
        catch(Exception e)
        {System.out.println("init error: "+e);} 				// вывод исключений
    }

    public Server(Socket s)
    {
        this.s = s;

        // и запускаем новый вычислительный поток (см. ф-ю run())
        setDaemon(true);
        setPriority(NORM_PRIORITY);
        start();
    }

    public void run()
    {
        try
        {

            InputStream is = s.getInputStream();												// из сокета клиента берём поток входящих данных
            OutputStream os = s.getOutputStream();												// и оттуда же - поток данных от сервера к клиенту
            byte buf[] = new byte[64*1024];														 // буффер данных в 64 килобайта
            int r = is.read(buf);																// читаем 64кб от клиента, результат - кол-во реально принятых данных
            String request = new String(buf, 0, r);												// создаём строку, содержащую полученую от клиента информацию
            String path = getPath(request);			 											// получаем путь до документа (см. ниже ф-ю "getPath")

            if(path == null)						            // если из запроса не удалось выделить путь, то возвращаем "400 Bad Request"
            {

                String response = "HTTP/1.1 400 Bad Request\n";	 // первая строка ответа
                DateFormat df = DateFormat.getTimeInstance();	 // дата в GMT
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                response = response + "Date: " + df.format(new Date()) + "\n";
                response = response								     // остальные заголовки
                        + "Connection: close\n"
                        + "Server: SimpleWEBServer\n"
                        + "Pragma: no-cache\n\n";
                os.write(response.getBytes());						 // выводим данные:
                s.close();             								// завершаем соединение
                return;
            }

            File f = new File(path);								            // если файл существует и является директорией, то ищем индексный файл index.html
            boolean flag = !f.exists();
            if(!flag) if(f.isDirectory())
            {
                if(path.lastIndexOf(""+File.separator) == path.length()-1)
                    path = path + "index.html";
                else
                    path = path + File.separator + "index.html";
                f = new File(path);
                flag = !f.exists();
            }

            if(flag)															            // если по указанному пути файл не найден то выводим ошибку "404 Not Found"
            {

                String response = "HTTP/1.1 404 Not Found\n";								// первая строка ответ
                DateFormat df = DateFormat.getTimeInstance();
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                response = response + "Date: " + df.format(new Date()) + "\n";

                response = response														// остальные заголовки
                        + "Content-Type: text/plain\n"
                        + "Connection: close\n"
                        + "Server: SimpleWEBServer\n"
                        + "Pragma: no-cache\n\n";

                response = response + "File " + path + " not found!";			// и гневное сообщение
                os.write(response.getBytes());									 // выводим данные:
                s.close();															// завершаем соединение
                return;
            }



            String mime = "text/plain";														// определяем MIME файла по расширению MIME по умолчанию - "text/plain"


            r = path.lastIndexOf("."); 														// выделяем у файла расширение (по точке)
            if(r > 0)
            {
                String ext = path.substring(r);
                if(ext.equalsIgnoreCase("html"))			mime = "text/html";
                else if(ext.equalsIgnoreCase("htm"))		mime = "text/html";
                else if(ext.equalsIgnoreCase("gif"))		mime = "image/gif";
                else if(ext.equalsIgnoreCase("jpg"))		mime = "image/jpeg";
                else if(ext.equalsIgnoreCase("jpeg"))		mime = "image/jpeg";
                else if(ext.equalsIgnoreCase("bmp"))		mime = "image/x-xbitmap";
            }

            // создаём ответ


            String response = "HTTP/1.1 200 OK\n";																		// первая строка ответа
            DateFormat df = DateFormat.getTimeInstance(); 																// дата создания в GMT
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            response = response + "Last-Modified: " + df.format(new Date(f.lastModified())) + "\n"; 					// время последней модификации файла в GMT
            response = response + "Content-Length: " + f.length() + "\n"; 												// длина файла
            response = response + "Content-Type: " + mime + "\n";														// строка с MIME кодировкой

            response = response																							// остальные заголовки
                    + "Connection: close\n"
                    + "Server: SimpleWEBServer\n\n";


            os.write(response.getBytes());																				 // выводим заголовок:
            FileInputStream fis = new FileInputStream(path);															// и сам файл:
            r = 1;
            while(r > 0)
            {
                r = fis.read(buf);
                if(r > 0) os.write(buf, 0, r);
            }
            fis.close();
            s.close(); 																									// завершаем соединение
        }
        catch(Exception e)
        {e.printStackTrace();} 																							// вывод исключений
    }




    protected String getPath(String header)															 // "вырезает" из HTTP заголовка URI ресурса и конвертирует его в filepath URI берётся только для GET и POST запросов, иначе возвращается null
    {
        // ищем URI, указанный в HTTP запросе
        // URI ищется только для методов POST и GET, иначе возвращается null
        String URI = extract(header, "GET ", " "), path;
        if(URI == null) URI = extract(header, "POST ", " ");
        if(URI == null) return null;

        // если URI записан вместе с именем протокола
        // то удаляем протокол и имя хоста
        path = URI.toLowerCase();
        if(path.indexOf("http://", 0) == 0)
        {
            URI = URI.substring(7);
            URI = URI.substring(URI.indexOf("/", 0));
        }
        else if(path.indexOf("/", 0) == 0)
            URI = URI.substring(1); // если URI начинается с символа /, удаляем его

        // отсекаем из URI часть запроса, идущего после символов ? и #
        int i = URI.indexOf("?");
        if(i > 0) URI = URI.substring(0, i);
        i = URI.indexOf("#");
        if(i > 0) URI = URI.substring(0, i);

        // конвертируем URI в путь до документов
        // предполагается, что документы лежат там же, где и сервер
        // иначе ниже нужно переопределить path
        path = PATH       //"." + File.separator;
        char a;
        for(i = 0; i < URI.length(); i++)
        {
            a = URI.charAt(i);
            if(a == '/')
                path = path + File.separator;
            else
                path = path + a;
        }

        return path;
    }



    protected String extract(String str, String start, String end)
    // "вырезает" из строки str часть, находящуюся между строками start и end
    // если строки end нет, то берётся строка после start
    // если кусок не найден, возвращается null
    // для поиска берётся строка до "\n\n" или "\r\n\r\n", если таковые присутствуют
    {
        int s = str.indexOf("\n\n", 0), e;
        if(s < 0) s = str.indexOf("\r\n\r\n", 0);
        if(s > 0) str = str.substring(0, s);
        s = str.indexOf(start, 0)+start.length();
        if(s < start.length()) return null;
        e = str.indexOf(end, s);
        if(e < 0) e = str.length();
        return (str.substring(s, e)).trim();
    }
}
