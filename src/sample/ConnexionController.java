/*_______________________________________________________________________________________
/*_______________________________R A N I A ____B E N C H E T I O U I ____________________
  ____________________________ S I M P L E   F T P    C L I E N T _______________________
  last edit >> 08/06/2020
 */
package sample;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.Notifications;
import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.StringTokenizer;

public class ConnexionController
{
    private static Socket dataSocket;
    private static PrintStream outputStream;
    private static BufferedReader inputStream;
    private static boolean loggedIn = false;
    private static long restartPoint = 0L;
    private static String lineTerm = "\n";


    @FXML
    public Label pwd_label;
    @FXML
    public TextField cwd_text_field;
    @FXML
    TextField username;
    @FXML
    PasswordField password;
    @FXML
    public ListView<String> listView;



    /**************_____________ Action Sur Btn >> login  ___________________**************/
    //au click au boutton login je change l'interface , je crée une socket pour établir la connexion et je fais le login
    public void handleActionBtn(ActionEvent event) throws IOException
    {
        connect();
        login();
        if (loggedIn)
        {
            notifier(""," logged in successfully ! ");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/ftp.fxml"));
            Parent parent =  loader.load();
            Scene mainScene = new Scene(parent);
            Stage window = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            window.setScene(mainScene);
            window.show();
        }
    }

    /**************_____________ C o n n e x i o n ___________________**************/

    private void connect()throws  IOException
    {

        //creation de la socket de connexion, qui va permettre l'echange de données avec le serveur
        Socket connectionSocket = new Socket("127.0.0.1", 21);

        //pour envoyer les donnees au serveur Flux general de sorties
        outputStream = new PrintStream(connectionSocket.getOutputStream());

        //pour lire la reponse du serveur Flux general d'entrées
        inputStream = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

        //tester si la réponse du serveur est positif sinon on annule la connexion
        String reply = getServerReply();
        //si les 3 num premmiéres de la réponse serveur != 220 => No connection
        if (Integer.parseInt(reply.substring(0, 3))!=220)
        {
            notifier("","Impossible d'établir la connexion ! ");
        }

        //nous sommes connectés
    }

    /*****************_____________ L o g i n  ___________________********************/
    private void login() throws IOException
    {
        int reponse ;
        //apporter le username + pass depuis le textfiled
        String ut = username.getText();
        String pass = password.getText();

        // Authentification
        reponse = executeCommand("user " + ut);
        if (reponse!=331)
        {
            System.out.println("Erreur de connexion avec le compte utilisateur : \n" + reponse);
        }
        reponse = executeCommand("pass " + pass);
        if(reponse!=230)
        {
            loggedIn=false;
            notifier("","username ou mot de pass erroné");
        }
        else
        {
            loggedIn = true;
        }
    }


    /*****************_____________ Avoir Toute La réponse du serveur ___________________********************/
    private String getServerReply() throws IOException {
        String reply;
        do
        {
            //recevoir reply du serveur
            reply = inputStream.readLine();

        }// tant que la reponse commence avec trois num
        while(!(Character.isDigit(reply.charAt(0)) &&
                Character.isDigit(reply.charAt(1)) &&
                Character.isDigit(reply.charAt(2)) &&
                reply.charAt(3) == ' '));
        return reply;
    }


    /*****************_____________ execution des commandes de controles >> envoi cmd + recevoir reponse  _____________********************/
    //envoyer une commande au serveur >> avoir la réponse sous forme de 3 num
    //pour les commandes de controles
    private int executeCommand(String command)throws IOException
    {
        //envoi de la commande
        outputStream.println(command);
        //recevoir la reponse
        String reply = getServerReply();
        return Integer.parseInt(reply.substring(0, 3));
    }

    /*****************_____________ execution des Commandes de paramètres de transfert >> envoi cmd + recevoir reponse  _____________********************/
    //envoyer une commande au serveur >> retourne vrai / faux
    //pour le reste des commandes
    private boolean executeDataCommand(String command, StringBuffer sb)throws IOException
    {
       //j'entre en mode passive + j'envoi la commande au serveur
        if (!setupDataPasv(command)) return false;
        //recevoir la reponse
        InputStream in = dataSocket.getInputStream();
        transferData(in,sb);

        //à chaque fois je dois fermer la socket de données  + fermer le input stream
        in.close();
        dataSocket.close();
        //recevoir la reponse
        String reply = getServerReply();
        int response=Integer.parseInt(reply.substring(0, 3));
        // tout les réponses entre cet intervalles >> Action demandée accomplie avec succès.
        return (response >= 200 && response < 300); // boolean
    }

    /*****************_____________ Ouvrir le mode passive  _____________********************/

    private boolean openPassiveMode() throws IOException
    {
        // envoyer une commande pour passer ne mode passive
        outputStream.println("PASV");
        //récupérer la reponse du serveur
        String tmp = getServerReply();


        String ip;
        int port;
        int debut = tmp.indexOf('(');
        int fin = tmp.indexOf(')', debut + 1);
        if (debut > 0) //si la reponse commence par '('
        {
            String dataLink = tmp.substring(debut + 1, fin); // récupérer les 4 numéros de l'adresse IP sous forme de num,num,num,num
            StringTokenizer tokenizer = new StringTokenizer(dataLink, ","); //isolé les numéros
            //séparer les numéros par des points pour avoir l'@ IP final
            ip = tokenizer.nextToken() + "." + tokenizer.nextToken() + "."
                    + tokenizer.nextToken() + "." + tokenizer.nextToken();

            //le numéro de port d'écoute est envoyé sous forme de 2 entiers >>
            port = Integer.parseInt(tokenizer.nextToken()) * 256
                    + Integer.parseInt(tokenizer.nextToken());

            //ouvrir une connexion de données selon l'@IP et le port d'écoute envoyé par le serveur
            dataSocket = new Socket(ip, port);
        }
        int response=Integer.parseInt(tmp.substring(0,3));
        return (response >= 200 && response < 300);
    }

    private  void transferData(InputStream in, StringBuffer sb) throws IOException
    {
        byte[] b = new byte[4096];
        int amount;

        // Stock les données dans un buffer
        while ((amount = in.read(b)) > 0)
        {
            sb.append(new String(b, 0, amount));
        }
    }

    /*****************_____________ Entrer en mode passive  _____________********************/
    private boolean setupDataPasv(String command) throws IOException
    {

        if (!openPassiveMode()) return false;

        // Lance le mode binaire pour la reception des donnees
        System.out.println("T R A N S F E R  MODE TYPE i");
        outputStream.println("TYPE i");

        //récupérer la reponse de serveur
        String reply = getServerReply();
        int response=Integer.parseInt(reply.substring(0, 3));

        //tester la reponse
        if (!(response >= 200 && response < 300))
        {
            System.out.println(" >> Could not set transfer type");
            return false;
        }

        // Si l'on a un point de restart

        if (restartPoint != 0)
        {
            System.out.println("rest " + restartPoint);
            //Cette commande spécifie un point de contrôle de données auquel un transfert de fichiers arrêté ou bloqué doit être redémarré.
            outputStream.println("rest " + restartPoint);
            //remettre le point de restart à 0 et récupérer la réponse du serveur
            restartPoint = 0;
            getServerReply();
        }
        //Envoyer la commande appropriée : RETR / STOR / DELE / ... etc
        System.out.println(command);
        outputStream.println(command);
        //interpréter la réponse du serveur
        String reply2 = getServerReply() ;
        int response2 =Integer.parseInt(reply2.substring(0, 3));
        return response2 >= 100 && response2 < 200;
    }


    /*****************_____________ recevoir la liste des documents / fichiers _____________********************/
    private String listFiles() throws IOException
    {
        return listFiles("");
    }

    private String listFiles(String params) throws IOException
    {
        StringBuffer files = new StringBuffer();
        StringBuffer dirs = new StringBuffer();
        if (!addFileDirToView(params, files, dirs))
        {
            System.out.println("Error");
        }
        //System.out.println(files.toString()+dirs.toString());
        return files.toString()+dirs.toString();
    }


    /*****************_____________ Ajouter les fichiers / documents dans la list view  _____________********************/

    private  boolean addFileDirToView(String params, StringBuffer files, StringBuffer dirs)throws IOException
    {
        // On initialise  0 les variables de retour
        files.setLength(0);
        dirs.setLength(0);

       //NLST >> avoir uniquement le nom du fichiers/documents
        String shortList = List_Nlst_Command("NLST " + params);
        //LIST >> avoir tout le nom fu fichiers/documents
        String longList = List_Nlst_Command("LIST " + params);

        // On tokenize les lignes récupérées
        StringTokenizer sList = new StringTokenizer(shortList, "\n");
        StringTokenizer lList = new StringTokenizer(longList, "\n");


        String sString;
        String lString;

        // les 2 lists ont le meme nombre de lignes.
        // hasMoreTokens >> L'appel de méthode renvoie «vrai» si et seulement s'il y a au moins un jeton dans la chaîne après la position actuelle; faux sinon.
        while ((sList.hasMoreTokens()) && (lList.hasMoreTokens()))  // tant qu'il existe une ligne faire
        {
            sString = sList.nextToken();
            lString = lList.nextToken();

            System.out.println(sString);
            //long string >> pour gérer les sorties /// short list >>  pour l'affichage
            if (lString.length() > 0)
            {
                if (lString.startsWith("-"))
                {
                    //liste des fichiers
                    listView.getItems().addAll(sString.trim() + "\t\t\t\t\t\t\t\t Size >> "+ (Integer.parseInt(size(sString))/1000)+" ko" +"\t\t\t"+ lineTerm);
                }
                else if (lString.startsWith("d"))
                {
                    //liste de documents
                    listView.getItems().add(sString.trim());
                }
            }
        }
        if (files.length() > 0)  {  files.setLength(files.length() - lineTerm.length());  }
        if (dirs.length() > 0)  {  dirs.setLength(dirs.length() - lineTerm.length());  }

        return true;
    }

    /*****************_____________ Envoyer cmd LIST et NLST  + recevoir la list des fichiers/documents depuis du serveur  _____________********************/
    private  String List_Nlst_Command(String command)throws IOException
    {
        StringBuffer reply = new StringBuffer();
        String replyString;

        boolean success = executeDataCommand(command, reply);
        //si l'envoie et la reception de la commande est bien réussi
        if (!success)
        {
            return "";
        }

        replyString = reply.toString();  // reponse de serveur ---> string

        if(reply.length() > 0)
        {
            return replyString.substring(0, reply.length() - 1); // je l'affiche
        }
        else
        {
            return replyString;
        }
    }

    /*****************_____________ commande STOR _____________********************/

    private void storeData(File file) throws IOException
    {
        //type >> ACSII
        executeCommand("TYPE ASCII");
        //ouvrir le mode passive
        openPassiveMode();
        //initialiser les I/O
        OutputStream outputStream = dataSocket.getOutputStream();

        //envoyer la commande stor + nom de fichier à uploader dans le serveur
        executeCommand("STOR "+file.getName());

        // récupérer la taille du fichier uploadé
        byte[] bytes = new byte[16 * 1024];
        //envoie du fichier
        InputStream in = new FileInputStream(file);
        int count;
        while ((count = in.read(bytes)) > 0)
        {
            outputStream.write(bytes, 0, count);
        }

        //fermer la socket de donnée
        dataSocket.close();
        outputStream.close();
    }

    /*****************_____________ commande RETR _____________********************/

    private void retrieveFile(String file, String path) throws IOException
    {

        //type >> ACSII
        executeCommand("TYPE ASCII");
        //ouvrir le mode passive
        openPassiveMode();
        //initialiser les I/O
        InputStream inputStream = dataSocket.getInputStream();

        //envoyer la commande retr + nom de fichier à télécharger depuis le serveur
        executeCommand("RETR "+file);


        RandomAccessFile outfile = new RandomAccessFile(file, "rw");
        // On lance un point de redémarage
        if (restartPoint != 0) {
            outfile.seek(restartPoint);
        }

        //le path >> ou le fichier doit étre enrégistré
        File f = new File(path+(char)47+file.trim());
        OutputStream out  = new FileOutputStream(f);
        byte[] bytes = new byte[16 * 1024];
        int count;
        while ((count = inputStream.read(bytes)) > 0)
        {
            out.write(bytes, 0, count);
        }
        dataSocket.close();
        inputStream.close();
        out.close();

    }

    /*****************_____________ commande SIZE pour récupérer la taille d'un fichier _____________********************/
    private String size(String fileName) throws IOException {
        outputStream.println("SIZE "+fileName);
        String reply = getServerReply() ;
        System.out.println("size >> "+reply);
        return (reply.substring(4));
}

    /*****************_____________ Action sur l'appui du btn >> list  _____________********************/
    public  void LIST() throws IOException
    {
        //afficher la liste des fichiers/documents
        listView.getItems().add(listFiles());

        // pour gérer le double click sue l'item
        listView.setOnMouseClicked(click -> {

            String filename = listView.getSelectionModel().getSelectedItem();
            int index = filename.indexOf("\t");

                if (click.getClickCount() == 2) {
                    if (index != -1)
                    {
                        System.out.println("Vous ne pouvez pas ouvrir un fichier ! ");
                        notifier("","Vous ne pouvez pas ouvrir un fichier !");
                    }
                    else
                    {
                    //utilisé l'élément sélèctionner de la list view
                    String currentItemSelected = listView.getSelectionModel()
                            .getSelectedItem();
                    try {
                        listView.getItems().clear();
                        cwd_(currentItemSelected);
                        LIST();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }

        });
    }

    /*****************_____________ Avoir le répèrtoire courant  _____________********************/
    public  void getCurrentDirectory() throws IOException
    {
        pwd_label.setText(pwd());
    }


    /*****************_____________ Commande >> pwd  _____________********************/
    private String pwd()throws IOException
    {
        //envoi de la commande et avoir la réponse
        outputStream.println("pwd");
        String response = getServerReply();
        //tokenizer la réponse
        StringTokenizer stroke = new StringTokenizer(response);

        // Get rid of the first token, which is the return code
        if (stroke.countTokens() < 2) return null;

        stroke.nextToken();
        String directoryName = stroke.nextToken();

        // Most servers surround the directory name with quotation marks
        int strlen = directoryName.length();
        if (strlen == 0) return null;
        if (directoryName.charAt(0) == '\"') {
            directoryName = directoryName.substring(1);
            strlen--;
        }
        if (directoryName.charAt(strlen - 1) == '\"') {
            return directoryName.substring(0, strlen - 1);
        }
        return directoryName;
    }

    /*****************_____________ Commande >> cwd >> changer le répertoire  _____________********************/
    private void cwd_(String cc) throws IOException
    {
        executeCommand("cwd " + cc);
    }
    /*****************_____________ Commande >> cwd >> changer le répertoire depuis une saisie de client _____________********************/
    public void setCwd_text_field( ) throws IOException
    {
        String directory = cwd_text_field.getText();
        executeCommand("cwd " + directory);
        listView.getItems().clear();
        LIST();

    }

    /*****************_____________ Appel >> méthode storeData _____________********************/

    public void store() throws IOException {

        //effacer la courante list view
        listView.getItems().clear();
        //demander la liste des fichiers/documents
        listFiles();
        //ouvrir une fenetre pour choisir un fichier pour l'uploader au serveur
        JFileChooser jfc = new JFileChooser();
        jfc.setCurrentDirectory(new File("E:\\downloads"));
        jfc.setDialogTitle("Serveur FTP");
        jfc.showOpenDialog(null);
        File file = jfc.getSelectedFile();
        String filename = file.getName();
        storeData(file);
        //afficher le nouveau élément
        listView.getItems().addAll(filename + "\t\t\t\t\t"+ (Integer.parseInt(size(filename))/1000)+" ko" + lineTerm);
    }

    /*****************_____________ Appel >> méthode retrieveFile _____________********************/
    public void retrieve() throws IOException
    {
        //afficher un fenetre our choisir ou placer le fichier qui va etre téléchargé
        String filename = listView.getSelectionModel().getSelectedItem();
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.showSaveDialog(null);
        String path = String.valueOf(fileChooser.getSelectedFile());
        int index = filename.indexOf("\t");
        if (index != -1)
        {
            filename = filename.substring(0,index);

            //téléchargement
            retrieveFile(filename,path);
        }


    }

    /*****************_____________ Commande >> DELE _____________********************/

    public void deleFile() throws IOException
    {
        //récupérer l'item sélèctionner == fichier ou document à supprimer
        String filename = listView.getSelectionModel().getSelectedItem();
        int index = filename.indexOf("\t");
        if (index != -1) //supprimer un fichier
        {
            filename = filename.substring(0,index);
            executeCommand("DELE "+filename);
            notifier(" ","file deleted successfully");
        }
        else
        { //supprimer d'abord les fichiers dans le document qu'on veut supprimer
            String shortList = List_Nlst_Command("NLST "+filename );
            // On tokenize les lignes récupérées
            StringTokenizer sList = new StringTokenizer(shortList, "\n");
            String sString;
            while ((sList.hasMoreTokens()) )
            {
                sString = sList.nextToken();
                executeCommand("DELE "+sString);
            }
            //Maintenant on supprime un repertoire vide
            executeCommand("RMD "+filename);
            notifier(" ","folder deleted successfully");
        }

        //afficher la listview aprés le delete
        listView.getItems().clear();
        LIST();


    }
    /*****************_____________ monter dans le repertoire ___________________********************/

    public void upFolder() throws IOException
    {
        executeCommand("CDUP");
        listView.getItems().clear();
        listFiles();
    }

    /****************___________ ajouter un nouveau document ______________*********************/
    public void addFolder() throws IOException
    {
        String inputValue = JOptionPane.showInputDialog("Folder name : ");
        executeCommand("XMKD "+inputValue);
        listView.getItems().clear();
        listFiles();
    }

    /****_________________ rename fichier  / document _______________________******************/
    public void rename() throws IOException
    {
        String input = JOptionPane.showInputDialog("rename to : ");
        String filename = listView.getSelectionModel().getSelectedItem();
        int index = filename.indexOf("\t");
        int index2 = filename.indexOf(".");

        if (index != -1) // c'est un fichier
        {
            //toute la chaine avec l'extension
            filename = filename.substring(0,index);
            // l'extention
            String ext = filename.substring(index2,index);

            //si le client a saisi l'extension
            if(input.contains(ext))
            {
                executeCommand("RNFR "+filename);
                executeCommand("RNTO "+input);
            }
            else
            {
                executeCommand("RNFR "+filename);
                executeCommand("RNTO "+input+ext);
            }

        }
        else // c'est un document
        {
            executeCommand("RNFR "+filename);
            executeCommand("RNTO "+input);
        }
        listView.getItems().clear();
        LIST();
    }

    /****************___________ se déconnecter ______________*****************/
    public void logout(ActionEvent event) throws IOException
    {
        //envoyer la commande au serveur FTP qui permet de se déconnecter de ce dernier
         outputStream.println("quit");

               //revenir au fenetre login
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/sample/fxml/connexion.fxml"));
                    Parent parent =  loader.load();
                    Scene mainScene = new Scene(parent);
                    Stage window = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                    window.setScene(mainScene);
                    window.show();

                    notifier(" ", "logged out successfully ! ");

    }

    /****************___________ Push notification ______________*****************/
    private static void notifier(String pTitle, String pMessage) {
        Platform.runLater(() -> {
                    Stage owner = new Stage(StageStyle.TRANSPARENT);
                    StackPane root = new StackPane();
                    root.setStyle("-fx-background-color: Transparent");
                    Scene scene = new Scene(root, 1, 1);
                    scene.setFill(Color.TRANSPARENT);
                    owner.setScene(scene);
                    owner.setWidth(1);
                    owner.setHeight(1);
                    owner.toBack();
                    owner.show();
                    Notifications.create().title(pTitle).text(pMessage).showInformation();
                }
        );
    }
}
