/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package co.com.claro.autodiagnosticoincidentesnegocios.services;

import client.runws.ProbeVO;
import client.runws.ServiceData;
import co.com.claro.autodiagnosticoincidentesnegocios.dto.AsociacionDTO;
import co.com.claro.autodiagnosticoincidentesnegocios.dto.ContentDTO;
import co.com.claro.autodiagnosticoincidentesnegocios.dto.EstadoEjecucion;
import co.com.claro.autodiagnosticoincidentesnegocios.soapClient.RelationCpeOltPeBean;
import com.claro.ppde.PingPlotterDF;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ejb.EJB;
import javax.ejb.Stateless;

/**
 *
 * @author gachae
 */
@Stateless
public class AutoDiagnosticoImpl implements AutoDiagnostico {

    @EJB
    private GetServicesSoap getRelationServiceSoap;

    @EJB
    private GetIncidentesServiceRest getIncidentesServiceRest;

    GetIncidentesServiceRestImpl getIncidentesServiceRestImpl = new GetIncidentesServiceRestImpl();
    GetServicesSoapImpl getServicesSoapImpl = new GetServicesSoapImpl();

    private List<ContentDTO.IncidentDFO> incidentDFOs = new ArrayList<>();
    private List<EstadoEjecucion> result = new ArrayList<>();

    @Override
    public void ejecutar() {
        System.out.println("***** Inicio Programa ****");
        long inicio = System.currentTimeMillis();
        System.out.println("*****Paso 1 ****");
        ContentDTO contentDTO = getIncidentesServiceRestImpl.getIncidentes();
        System.out.println("***** Cantidad a Procesar :  " + contentDTO.getContent().size());
        for (ContentDTO.ContentItem arg : contentDTO.getContent()) {
            incidentDFOs.add(arg.getIncidentDFO());
        }
        //Paso 2 : Recuperar los idServicio y enviarlos al servicio Soap
        int valor = 0;
        for (ContentDTO.IncidentDFO incidente : incidentDFOs) {
            try {

                System.out.println("***** Evaluando :  " + "Service Id:" + incidente.getIncidentID());
                System.out.println("***** Paso 2 :  ");

                List<RelationCpeOltPeBean> bean = getServicesSoapImpl.getRelation(incidente.getIncidentID());
                if (bean.isEmpty()) {
                    System.out.println("***** Services Id No encontrado :   " + incidente.getIncidentID());
                } else {
                    System.out.println("*****  Paso 3 :");
                    // Preparar los parámetros para el método run
                    String testName = "CPE_Ping_Cpe";

                    // Crear y configurar el objeto ServiceData
                    ServiceData serviceData = new ServiceData();
                    serviceData.setCpeHostname(bean.get(0).getCpeHostname());
                    serviceData.setCpeIp(Optional.of(bean.get(0).getCpeIp()).orElse(""));
                    serviceData.setCpeMac(Optional.of(bean.get(0).getCpeMac()).orElse(""));
                    serviceData.setCpeVendor(Optional.of(bean.get(0).getCpeVendor()).orElse(""));
                    serviceData.setPeHostname(Optional.of(bean.get(0).getPeHostname()).orElse(""));
                    serviceData.setPeInterface(Optional.of(bean.get(0).getPeInterface()).orElse(""));
                    serviceData.setPeIp(Optional.of(bean.get(0).getPeIp()).orElse(""));
                    serviceData.setPeService(Optional.of(bean.get(0).getPeService()).orElse(""));
                    serviceData.setPeVrf(Optional.of(bean.get(0).getPeVrf()).orElse(""));
                    serviceData.setSwBrand(Optional.of(bean.get(0).getSwBrand()).orElse(""));
                    serviceData.setSwDesc(Optional.of(bean.get(0).getSwDesc()).orElse(""));
                    serviceData.setSwHostname(Optional.of(bean.get(0).getSwHostname()).orElse(""));
                    serviceData.setSwInterface(Optional.of(bean.get(0).getSwInterface()).orElse(""));
                    serviceData.setSwIp(Optional.of(bean.get(0).getSwIp()).orElse(""));

                    // Invocar el método run
                    System.out.println("*****  Paso 4 :");
                    String runResult = getServicesSoapImpl.getUltimaMilla(testName, serviceData);
                    System.out.println("*****  Consumo ultima Milla :");

                    String evaluacion = "0% packet loss".equals(validacionPacketLoss(runResult)) ? "Up" : "Down";

                    System.out.println("Ultima milla: " + evaluacion);

                    System.out.println("*****  Paso 5 :");

                    String interfaceSW = bean.get(0).getSwInterface();
                    String swIp = bean.get(0).getSwIp();
                    String swHostName = bean.get(0).getSwHostname();
                    String evaluacionTitulo = swHostName + " " + swIp;

                    if (bean.get(0).getSwBrand().equalsIgnoreCase("huawei")) {
                        String estadoTroncal = getServicesSoapImpl.getEstadoTroncal(interfaceSW.split(":")[0], swIp);

                        System.out.println("Estado troncal Huawei" + estadoTroncal);

                        if (estadoTroncal.equalsIgnoreCase("down")) {
                            List<PingPlotterDF> listAvisos = getServicesSoapImpl.getListadoAvisos();
                            for (PingPlotterDF pingPlotterDF : listAvisos) {
                                if (pingPlotterDF.getTitle().contains(evaluacionTitulo)) {
                                    AsociacionDTO asociacionDTO = new AsociacionDTO(
                                            incidente.getIncidentID(),
                                            incidente.getOpenTime(),
                                            incidente.getStatus(),
                                            incidente.getAssignmentGroup(),
                                            incidente.getTitle(),
                                            incidente.getUserPriority(),
                                            "AutoTicket",
                                            incidente.getCIAfectado(),
                                            bean.get(0).getCpeIp());
                                }
                            }

                        }

                    } else if (bean.get(0).getSwBrand().equalsIgnoreCase("zte")) {
                        String estadoTroncal = getServicesSoapImpl.getEstadoTroncal(interfaceSW.split(":")[0], swIp);
                        System.out.println("Estado troncal ZTE" + estadoTroncal);
                        if (estadoTroncal.equalsIgnoreCase("down")) {
                            List<PingPlotterDF> listAvisos = getServicesSoapImpl.getListadoAvisos();
                            for (PingPlotterDF pingPlotterDF : listAvisos) {
                                if (pingPlotterDF.getTitle().contains(evaluacionTitulo)) {
                                    //La data se recupera del listado de IM: 
                                    AsociacionDTO asociacionDTO = new AsociacionDTO(
                                            incidente.getIncidentID(),
                                            incidente.getOpenTime(),
                                            incidente.getStatus(),
                                            incidente.getAssignmentGroup(),
                                            incidente.getTitle(),
                                            incidente.getUserPriority(),
                                            "AutoTicket",
                                            incidente.getCIAfectado(),
                                            bean.get(0).getCpeIp());
                                    getServicesSoapImpl.asoiciarNotaIM(asociacionDTO);
                                    System.out.println("Titulo encontrado para zte");
                                }
                            }
                        }
                    }

                    System.out.println("Consumo ultima milla: " + runResult.toString());
                    System.out.println("***** Finalizacion ....:");
                    valor++;
                    System.out.println("Posición: " + valor);
                }
            } catch (Exception ex) {
                Logger.getLogger(AutoDiagnosticoImpl.class.getName()).log(Level.ALL.SEVERE, null, ex);
            }
        }

        long fin = System.currentTimeMillis();
        double tiempo = (double) ((fin - inicio) / 1000);

        System.out.println(tiempo + " segundos");

        for (EstadoEjecucion arg : result) {
            System.out.println("Resultado de Ejecucion" + arg.toString());

        }

    }

    public static String validacionPacketLoss(String pingResponse) {

        String packetLoss = "No se encontró información de packet loss";

        // Define el patrón regex para encontrar la línea de pérdida de paquetes
        Pattern pattern = Pattern.compile("(\\d+)% packet loss");
        Matcher matcher = pattern.matcher(pingResponse);

        // Si encontramos un match, lo devolvemos
        if (matcher.find()) {
            packetLoss = matcher.group(1) + "% packet loss";
        }
        return packetLoss;
    }

}
