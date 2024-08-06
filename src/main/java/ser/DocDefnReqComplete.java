package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.IProcessInstance;
import com.ser.blueline.bpm.ITask;
import com.ser.blueline.bpm.IWorkbasket;
import com.ser.blueline.metaDataComponents.IArchiveClass;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;


public class DocDefnReqComplete extends UnifiedAgent {
    Logger log = LogManager.getLogger();
    IProcessInstance processInstance;
    IInformationObject qaInfObj;
    ProcessHelper helper;
    ITask task;
    IInformationObject document;
    String compCode;
    String reqId;
    @Override
    protected Object execute() {
        if (getEventTask() == null)
            return resultError("Null Document object");

        if(getEventTask().getProcessInstance().findLockInfo().getOwnerID() != null){
            return resultRestart("Restarting Agent");
        }

        Utils.session = getSes();
        Utils.bpm = getBpm();
        Utils.server = Utils.session.getDocumentServer();
        Utils.loadDirectory(Conf.Paths.MainPath);
        
        task = getEventTask();

        try {

            helper = new ProcessHelper(Utils.session);
            XTRObjects.setSession(Utils.session);

            processInstance = task.getProcessInstance();

            document = processInstance.getMainInformationObject();
            if(document == null){
                List<ILink> aLnks = processInstance.getLoadedInformationObjectLinks().getLinks();
                document = (!aLnks.isEmpty() ? aLnks.get(0).getTargetInformationObject() : document);
                if(document != null) {
                    processInstance.setMainInformationObjectID(document.getID());
                }
            }
            if(document == null){
                throw new Exception("Document not found.");
            }

            String mfld = processInstance.getDescriptorValue(Conf.Descriptors.GIB_MainFolder, String.class);
            mfld = (mfld == null ? "" : mfld);
            if(mfld.isEmpty()){
                throw new Exception("_MainFolder not set.");
            }

            String dtyp = processInstance.getDescriptorValue(Conf.Descriptors.GIB_DocumentType, String.class);
            dtyp = (dtyp == null ? "" : dtyp);
            if(dtyp.isEmpty()){
                throw new Exception("_DocType not set.");
            }

            document.setDescriptorValue(Conf.Descriptors.GIB_MainFolder, mfld);
            document.setDescriptorValue(Conf.Descriptors.GIB_DocumentType, dtyp);
            document.commit();

            String gjra = "__JiraResponsibles";
            IGroup egrp = XTRObjects.findGroup(gjra);
            if(egrp == null){
                egrp = XTRObjects.createGroup(gjra);
                egrp.commit();
            }
            if(egrp == null){throw new Exception("Not found/create group '" + gjra + "'");}

            IWorkbasket gwbk = XTRObjects.getFirstWorkbasket(egrp);
            if(gwbk == null){
                gwbk = XTRObjects.createWorkbasket(egrp);
                gwbk.commit();
            }
            if(gwbk == null){throw new Exception("Not found/create workbasket '" + gjra + "'");}

            IDocument docGIB = copyDocument(document, mfld);
            Utils.copyDescriptors(document, docGIB);
            docGIB.setDescriptorValue("Sender", gwbk.getID());
            docGIB.commit();

            log.info("Tested.");

        } catch (Exception e) {
            //throw new RuntimeException(e);
            log.error("Exception       : " + e.getMessage());
            log.error("    Class       : " + e.getClass());
            log.error("    Stack-Trace : " + Arrays.toString(e.getStackTrace()));
            return resultError("Exception : " + e.getMessage());
        }

        log.info("Finished");
        return resultSuccess("Ended successfully");
    }
    public static IDocument copyDocument(IInformationObject docu, String dtyp) throws Exception {
        IArchiveClass ac = Utils.server.getArchiveClassByName(Utils.session, dtyp.trim());
        if(ac == null){throw new Exception("ArchiveClass not found (Name : " + dtyp + ")");}
        IDatabase db = Utils.session.getDatabase(ac.getDefaultDatabaseID());
        IDocument rtrn = Utils.server.getClassFactory().getDocumentInstance(db.getDatabaseName(), ac.getID(), "0000" , Utils.session);
        rtrn = Utils.server.copyDocument2(Utils.session, (IDocument) docu, rtrn, CopyScope.COPY_PART_DOCUMENTS);
        return rtrn;
    }
}