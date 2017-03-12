package service;


import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ThreadLocalRandom;

import dao.AddDao;
import dao.ClientDao;
import dao.CommuneDao;
import dao.CompteLbcDao;
import exception.AgentLbcFailPublicationException;
import exception.NoAddsOnlineException;
import scraper.Add;
import scraper.AddCategory;
import scraper.AddsGenerator;
import scraper.AgentLbc;
import scraper.Client;
import scraper.Commune;
import scraper.CompteLbc;
import scraper.CritereSelectionTitre;
import scraper.CriteresSelectionTexte;
import scraper.CriteresSelectionVille;
import scraper.ParametresPublication;
import scraper.PathToAdds;
import scraper.ResultsControl;
import scraper.Source;
import scraper.Texte;
import scraper.TexteAndTitleManager;
import scraper.Title;
import scraper.TypeTexte;
import scraper.TypeTitle;

public class ObjectManager {

	private List<Client> clients;
	private int nbAddsToPublish;
	private int nbAddsPublie;

	private AgentLbc agentLbc;
	private AddsGenerator addsGenerator;
	private AddsSaver addsSaver;

	private HashMap<Integer, CompteLbc> comptes;
	private CompteLbc compteInUse;

	private List<Title> titleSource;
	private List<Texte> texteSource;
	private List<Commune> communeSource;

	private CritereSelectionTitre critSelectTitre;
	private CriteresSelectionVille critSelectVille;
	private CriteresSelectionTexte critSelectTexte;

	private PathToAdds pathToAdds; 

	private List<Add> addsFromLbc;
	private List<Add> addsPublieAvtMode;
	private boolean preparationOk;
	private boolean savingOk; 
	private boolean saveAddToSubmitLbcInBase;
	// DAO
	private CommuneDao comDao = new CommuneDao();
	// r�sultats contr�le
	private ResultsControl results;
	private Client clientInUse;

	public ResultsControl getResults() {
		return results;
	}

	

	public List<Commune> search(String nameCommuneInBdd){
		List<Commune> communes = comDao.findAll(nameCommuneInBdd);
		return communes;
	}

	public void scanAddsOnLbc() throws NoAddsOnlineException{// pour r�cup�rer les annonces telles qu'elles sont sur lbc
		agentLbc.setUp();
		agentLbc.connect();
		try{
			this.addsFromLbc = agentLbc.scanAddsOnLbc();
		}catch(NoAddsOnlineException excep){
			AddDao addDao = new AddDao();
			CompteLbcDao compteLbcDao = new CompteLbcDao();
			System.out.println("Le compte en utilisation est : "+compteInUse.getRefCompte());
			compteLbcDao.updateDateDernierControl(compteInUse);
			excep.setStatsOnAdds(addDao.putAllsAddsNotOnline(compteInUse));
			
			throw excep;
		}
		

	}
	public boolean isTexteAndTitleOnlineReferenced(){
		addsSaver = new AddsSaver(addsFromLbc, compteInUse);
		// pour commencer � lier les �l�ments des annonces en ligne (titres, communes, textes) � la bdd
		// la fin des correspondances pourra se faire manuellement
		return(addsSaver.isTexteAndTitleOnlineReferenced());
	}

	// peut �tre appel� plusieurs jusqu'� temps que les adds avec ref_mutiple soit r�f�renc�s une seule fois
	public boolean hasAddsWithMultipleReferenced(){
		addsSaver.classifyAddsOnlineWithTitleAndTextReferenced();
		return(addsSaver.hasAddsWithMultipleReferenced());
	}
	
	// doit �tre appel� qu'une fois et doit retourner forc�ment true
	public boolean isReadyToSave(){
		addsSaver.setSubmitCommuneAndRefAddForAddsOnlineReferenced();
		addsSaver.saveAddsOnlineNotReferenced();
		return(addsSaver.isReadyToSave());
	}
	
	public void saveAddsFromScanOfLbc(){
			addsSaver.updateAddsFromLbc();
			results = addsSaver.getResults();
	}

	public void genererEtPublier() {
		// g�n�ration des annonces
		addsGenerator.setImage();
		addsGenerator.generateAdds();
		agentLbc.setAddsToPublish(addsGenerator.getaddsProduced());
		agentLbc.setUp();
		agentLbc.connect();
		agentLbc.goToFormDepot();
		try{
			addsPublieAvtMode = agentLbc.publish();
			this.nbAddsPublie = this.nbAddsToPublish-agentLbc.getAddsWithCommuneNotRecognised().size();
		}catch(AgentLbcFailPublicationException excep){
			this.nbAddsPublie = excep.getIndiceAnnonceDechec();
			addsPublieAvtMode = excep.getAddsToPublish();
		}
	}

	public void setcommunes() {
		addsGenerator.setCommuneSource(clientInUse);
		communeSource = addsGenerator.getCommuneSource();
	}

	public CriteresSelectionVille getCritSelectVille() {
		return critSelectVille;
	}

	public void setCritSelectVille(int borneInf, int bornSup) {
		this.critSelectVille = new CriteresSelectionVille();
		critSelectVille.setBornInfPop(borneInf);
		critSelectVille.setBornSupPop(bornSup);
		addsGenerator.setCritSelectVille(this.critSelectVille);
	}


	public void createAddsGenerator(){
		addsGenerator = new AddsGenerator(nbAddsToPublish, this.compteInUse);
	}

	public void setTextes() {
		addsGenerator.setTexteSource();
		texteSource = addsGenerator.getTexteSource();
	}

	public void setTitres(){
		addsGenerator.setTitleSource();
		titleSource = addsGenerator.getTitleSource();
	}

	// pour r�cup�rer tous les comptes
	public void setComptes(){
		CompteLbcDao compteDao = new CompteLbcDao();
		comptes = compteDao.findAll(clientInUse); 
	}

	public void setCompte(int identifiant){
				compteInUse = comptes.get(identifiant);
	}




	public void setPathToAdds(String pathToAdds) {
		this.pathToAdds = PathToAdds.valueOf(pathToAdds);
		addsGenerator.setPathToAddsDirectory(this.pathToAdds.getPath());
	}

	public int getNbAddsToPublish() {
		return nbAddsToPublish;
	}

	public void setNbAddsToPublish(int nbAddsToPublish) {
		this.nbAddsToPublish = nbAddsToPublish;
	}

	public void createAgentLbc(int nbAddsToPublish, String afficherNumTel, String numTel, String category){
		boolean numTelOnAdds = false;
		if(afficherNumTel.equals("oui")){
			numTelOnAdds=true;
		}else{
			numTel="0";
			for(int i=0;i<=9;i++){
				int randomNum = ThreadLocalRandom.current().nextInt(0, 10);
				numTel=numTel+randomNum;
			}
		}
		ParametresPublication paras = new ParametresPublication();
		paras.setAfficherNum(numTelOnAdds);
		paras.setNbDannoncesAPublier(nbAddsToPublish);
		paras.setNumTelephone(numTel);
		paras.setAddCategory(AddCategory.valueOf(category));
		agentLbc = new AgentLbc(compteInUse, saveAddToSubmitLbcInBase, paras);
		setNbAddsToPublish(nbAddsToPublish);
	}

	public void createAgentLbc(){
		agentLbc = new AgentLbc(compteInUse);
	}

	public CompteLbc getCompteInUse() {
		return compteInUse;
	}

	public HashMap<Integer, CompteLbc> getComptes() {
		return comptes;
	}
	
	public List<CompteLbc> getValuesComptes() {
		return new ArrayList<CompteLbc>(comptes.values());
	}

	public void setTitleSourceType(String titleSourceType) {
		addsGenerator.setTypeSourceTitles(Source.valueOf(titleSourceType));
	}

	public void setTexteSourceType(String texteSourceType) {
		addsGenerator.setTypeSourceTextes(Source.valueOf(texteSourceType));
	}

	public void setCommuneSourceType(String communeSourceType) {
		addsGenerator.setTypeSourceCommunes(Source.valueOf(communeSourceType));
	}

	public AgentLbc getAgentLbc() {
		return agentLbc;
	}

	public void setAgentLbc(AgentLbc agentLbc) {
		this.agentLbc = agentLbc;
	}

	public AddsGenerator getAddsGenerator() {
		return addsGenerator;
	}

	public void setAddsGenerator(AddsGenerator addsGenerator) {
		this.addsGenerator = addsGenerator;
	}

	public List<Title> getTitleSource() {
		return titleSource;
	}

	public void setTitleSource(List<Title> titleSource) {
		this.titleSource = titleSource;
	}

	public List<Texte> getTexteSource() {
		return texteSource;
	}

	public void setTexteSource(List<Texte> texteSource) {
		this.texteSource = texteSource;
	}

	public List<Commune> getCommuneSource() {
		return communeSource;
	}

	public void setCommuneSource(List<Commune> communeSource) {
		this.communeSource = communeSource;
	}

	public CritereSelectionTitre getCritSelectTitre() {
		return critSelectTitre;
	}

	public void setCritSelectTitre(String typeTitle) {
		this.critSelectTitre = new CritereSelectionTitre(TypeTitle.valueOf(typeTitle));
		addsGenerator.setCritSelectTitre(this.critSelectTitre);
	}

	public void setCritSelectTexte(String typeTexte) {
		this.critSelectTexte = new CriteresSelectionTexte(TypeTexte.valueOf(typeTexte));
		addsGenerator.setCritSelectTexte(this.critSelectTexte);
	}

	public PathToAdds getPathToAdds() {
		return pathToAdds;
	}

	public void setPathToAdds(PathToAdds pathToAdds) {
		this.pathToAdds = pathToAdds;
	}

	public void setCompteInUse(CompteLbc compteInUse) {
		this.compteInUse = compteInUse;
	}

	public boolean isPreparationOk() {
		return preparationOk;
	}

	public void setPreparationOk(boolean preparationOk) {
		this.preparationOk = preparationOk;
	}

	public boolean isSavingOk() {
		return savingOk;
	}

	public void setSavingOk(boolean savingOk) {
		this.savingOk = savingOk;
	}

	public boolean isSaveAddToSubmitLbcInBase() {
		return saveAddToSubmitLbcInBase;
	}

	public void setSaveAddToSubmitLbcInBase(boolean saveAddToSubmitLbcInBase) {
		this.saveAddToSubmitLbcInBase = saveAddToSubmitLbcInBase;
	}

	public int getNbAddsPublie() {
		return nbAddsPublie;
	}

	public void setNbAddsPublie(int nbAddsPublie) {
		this.nbAddsPublie = nbAddsPublie;
	}

	public List<Add> getAddsPublieAvtMode() {
		return addsPublieAvtMode;
	}

	public AddsSaver getAddsSaver() {
		return addsSaver;
	}

	public void setClients() {
		ClientDao clientDao = new ClientDao();
		this.clients  = clientDao.findAll();
	}



	public List<Client> getClients() {
		return clients;
	}



	public void setClientInUse(int refClientChoisie) {
		for(Client client : clients){
			if(client.getRefClient()==refClientChoisie){
				clientInUse = client;
				return;
			}
		}
		
	}



	public void addNewClient(String nom, String prenom) {
		Client client = new Client();
		client.setnomClient(nom);
		client.setPrenomClient(prenom);
		ClientDao clientDao = new ClientDao();
		clientDao.save(client);
	}

	public Client getClientInUse() {
		return clientInUse;
	}
	
	


















}
