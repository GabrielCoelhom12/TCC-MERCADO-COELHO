package br.com.coelho.escalas;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regras de arquitetura verificadas a cada build: as dependências entre os pacotes seguem um único sentido
 * (api → servico → motor/repositorio → dominio), como descrito em docs/arquitetura.md.
 */
@AnalyzeClasses(packages = "br.com.coelho.escalas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    private static final String RAIZ = "br.com.coelho.escalas.";

    @ArchTest
    static final ArchRule dominioNaoDependeDasOutrasCamadas = noClasses()
            .that().resideInAPackage(RAIZ + "dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RAIZ + "api..", RAIZ + "servico..", RAIZ + "motor..", RAIZ + "repositorio..", RAIZ + "seguranca..",
                    RAIZ + "config..");

    /** O motor é Java puro: recebe tudo pelo ContextoCalculo e pode ser testado sem banco nem servidor. */
    @ArchTest
    static final ArchRule motorNaoAcessaBancoNemWeb = noClasses()
            .that().resideInAPackage(RAIZ + "motor..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RAIZ + "api..", RAIZ + "servico..", RAIZ + "repositorio..", RAIZ + "seguranca..", RAIZ + "config..",
                    "jakarta.persistence..", "org.springframework.data..", "org.springframework.web..");

    @ArchTest
    static final ArchRule repositoriosSoConhecemODominio = noClasses()
            .that().resideInAPackage(RAIZ + "repositorio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RAIZ + "api..", RAIZ + "servico..", RAIZ + "motor..", RAIZ + "seguranca..", RAIZ + "config..");

    /** Serviços não conhecem HTTP (requisição, resposta, códigos de status): isso é papel dos controllers. */
    @ArchTest
    static final ArchRule servicosNaoDependemDaCamadaHttp = noClasses()
            .that().resideInAPackage(RAIZ + "servico..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RAIZ + "api..", "org.springframework.http..", "org.springframework.web.bind..", "jakarta.servlet..");

    /** Controllers só tratam HTTP: regras de negócio e acesso a dados ficam nos serviços. */
    @ArchTest
    static final ArchRule controllersNaoAcessamRepositorios = noClasses()
            .that().resideInAPackage(RAIZ + "api..")
            .should().dependOnClassesThat().resideInAPackage(RAIZ + "repositorio..");
}
