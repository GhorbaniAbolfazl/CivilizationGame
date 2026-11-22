package civilization.phase3;

import civilization.client.event.ClientMessageRouter;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.state.ClientStateStore;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.protocol.AcceptTradeOfferRequest;
import civilization.protocol.CancelTradeOfferRequest;
import civilization.protocol.CreateTradeOfferRequest;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.GameStatePush;
import civilization.protocol.MessageMetadata;
import civilization.protocol.PushMessage;
import civilization.protocol.RejectTradeOfferRequest;
import civilization.protocol.ResourceBundleDto;
import civilization.protocol.TradeOfferPush;
import civilization.server.lobby.LobbyServerService;
import civilization.server.match.MultiplayerGameState;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.server.trade.PlayerTradeException;
import civilization.server.trade.PlayerTradeStatus;
import civilization.view.game.PlayerTradePanel;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class Phase3PlayerTradeTestSuite {
    // PHASE 3 - Added in Phase 3
    private static final Instant NOW = Instant.parse("2032-06-07T08:09:10.123456Z");
    // PHASE 3 - Added in Phase 3
    private Phase3PlayerTradeTestSuite() {}

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        creationLocksResourcesAndPushesOnlyToParties();
        acceptanceIsAtomicAndCannotRunTwice();
        rejectionAndCancellationReleaseExactlyOnce();
        invalidInsufficientAndUnauthorizedOffersDoNotMutateState();
        simultaneousDecisionsCannotDuplicateResources();
        clientInboxUpdatesOnTheEdt();
        System.out.println("Phase3PlayerTradeTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void creationLocksResourcesAndPushesOnlyToParties() {
        try (Fixture fixture = new Fixture(true)) {
            ResourceStockpile stockpile = fixture.stockpile(fixture.host);
            int total = stockpile.amountOf(ResourceType.FOOD);
            var result = fixture.create(fixture.host, fixture.guest, 4, 3, 1L);
            assert result.offer().status() == PlayerTradeStatus.PENDING;
            assert result.offer().createdAt().equals(NOW);
            assert !result.offer().offerId().isBlank();
            assert stockpile.amountOf(ResourceType.FOOD) == total;
            assert stockpile.reservedAmountOf(ResourceType.FOOD) == 4;
            assert stockpile.availableAmountOf(ResourceType.FOOD) == total - 4;
            assert fixture.latestTrade(fixture.host.sessionId()).offerId().equals(result.offer().offerId());
            assert fixture.latestTrade(fixture.guest.sessionId()).offerId().equals(result.offer().offerId());
            assert fixture.countTrade(fixture.observer.sessionId()) == 0;
            GameStatePush senderState = fixture.latestState(fixture.host.sessionId());
            assert senderState.snapshot().viewerResources().get("FOOD") == total - 4;
            // PHASE 3 - Added in Phase 3
            try {
                stockpile.debit(ResourceBundle.of(ResourceType.FOOD, total));
                throw new AssertionError("Locked resources were spendable");
            } catch (IllegalStateException expected) {
                assert stockpile.reservedAmountOf(ResourceType.FOOD) == 4;
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void acceptanceIsAtomicAndCannotRunTwice() {
        try (Fixture fixture = new Fixture(false)) {
            String offerId = fixture.create(fixture.host, fixture.guest, 4, 3, 1L).offer().offerId();
            fixture.endHostTurn(2L);
            ResourceStockpile sender = fixture.stockpile(fixture.host);
            ResourceStockpile recipient = fixture.stockpile(fixture.guest);
            int senderFood = sender.amountOf(ResourceType.FOOD);
            int senderWood = sender.amountOf(ResourceType.WOOD);
            int recipientFood = recipient.amountOf(ResourceType.FOOD);
            int recipientWood = recipient.amountOf(ResourceType.WOOD);
            fixture.service.acceptTradeOffer(fixture.guest, new AcceptTradeOfferRequest(
                    MessageMetadata.createNow(), fixture.matchId, offerId, 3L)).join();
            assert sender.amountOf(ResourceType.FOOD) == senderFood - 4;
            assert sender.amountOf(ResourceType.WOOD) == senderWood + 3;
            assert recipient.amountOf(ResourceType.FOOD) == recipientFood + 4;
            assert recipient.amountOf(ResourceType.WOOD) == recipientWood - 3;
            assert sender.reservedAmountOf(ResourceType.FOOD) == 0;
            assert fixture.latestTrade(fixture.guest.sessionId()).status().equals("ACCEPTED");
            int totalFood = sender.amountOf(ResourceType.FOOD) + recipient.amountOf(ResourceType.FOOD);
            int totalWood = sender.amountOf(ResourceType.WOOD) + recipient.amountOf(ResourceType.WOOD);
            expectTrade("TRADE_OFFER_CLOSED", () -> fixture.service.acceptTradeOffer(fixture.guest,
                    new AcceptTradeOfferRequest(MessageMetadata.createNow(), fixture.matchId,
                            offerId, 4L)).join());
            assert totalFood == sender.amountOf(ResourceType.FOOD) + recipient.amountOf(ResourceType.FOOD);
            assert totalWood == sender.amountOf(ResourceType.WOOD) + recipient.amountOf(ResourceType.WOOD);
            assert fixture.version() == 4L;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void rejectionAndCancellationReleaseExactlyOnce() {
        try (Fixture fixture = new Fixture(false)) {
            ResourceStockpile sender = fixture.stockpile(fixture.host);
            String cancelled = fixture.create(fixture.host, fixture.guest, 2, 1, 1L).offer().offerId();
            fixture.service.cancelTradeOffer(fixture.host, new CancelTradeOfferRequest(
                    MessageMetadata.createNow(), fixture.matchId, cancelled, 2L)).join();
            assert sender.reservedAmountOf(ResourceType.FOOD) == 0;
            expectTrade("TRADE_OFFER_CLOSED", () -> fixture.service.cancelTradeOffer(fixture.host,
                    new CancelTradeOfferRequest(MessageMetadata.createNow(), fixture.matchId,
                            cancelled, 3L)).join());
        }
        try (Fixture fixture = new Fixture(false)) {
            ResourceStockpile sender = fixture.stockpile(fixture.host);
            String rejected = fixture.create(fixture.host, fixture.guest, 2, 1, 1L).offer().offerId();
            fixture.endHostTurn(2L);
            fixture.service.rejectTradeOffer(fixture.guest, new RejectTradeOfferRequest(
                    MessageMetadata.createNow(), fixture.matchId, rejected, 3L)).join();
            assert sender.reservedAmountOf(ResourceType.FOOD) == 0;
            assert fixture.latestTrade(fixture.host.sessionId()).status().equals("REJECTED");
            expectTrade("TRADE_OFFER_CLOSED", () -> fixture.service.rejectTradeOffer(fixture.guest,
                    new RejectTradeOfferRequest(MessageMetadata.createNow(), fixture.matchId,
                            rejected, 4L)).join());
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void invalidInsufficientAndUnauthorizedOffersDoNotMutateState() {
        try (Fixture fixture = new Fixture(false)) {
            expectTrade("INVALID_TRADE_TARGET", () -> fixture.service.createTradeOffer(fixture.host,
                    fixture.request(fixture.host, fixture.host, bundle("FOOD", 1),
                            bundle("WOOD", 1), 1L)).join());
            expectTrade("INVALID_TRADE_RESOURCE", () -> fixture.service.createTradeOffer(fixture.host,
                    fixture.request(fixture.host, fixture.guest, bundle("GOLD", 1),
                            bundle("WOOD", 1), 1L)).join());
            int available = fixture.stockpile(fixture.host).availableAmountOf(ResourceType.FOOD);
            expectTrade("INSUFFICIENT_TRADE_RESOURCES", () -> fixture.service.createTradeOffer(fixture.host,
                    fixture.request(fixture.host, fixture.guest, bundle("FOOD", available + 1L),
                            bundle("WOOD", 1), 1L)).join());
            assert fixture.version() == 1L;
            String offerId = fixture.create(fixture.host, fixture.guest, 1, Integer.MAX_VALUE, 1L)
                    .offer().offerId();
            expectTrade("UNAUTHORIZED_TRADE_ACTION", () -> fixture.service.acceptTradeOffer(fixture.host,
                    new AcceptTradeOfferRequest(MessageMetadata.createNow(), fixture.matchId,
                            offerId, 2L)).join());
            assert fixture.version() == 2L;
            fixture.endHostTurn(2L);
            expectTrade("TRADE_RESOURCES_UNAVAILABLE", () -> fixture.service.acceptTradeOffer(fixture.guest,
                    new AcceptTradeOfferRequest(MessageMetadata.createNow(), fixture.matchId,
                            offerId, 3L)).join());
            assert fixture.version() == 3L;
            expectTrade("UNAUTHORIZED_TRADE_ACTION", () -> fixture.service.cancelTradeOffer(fixture.guest,
                    new CancelTradeOfferRequest(MessageMetadata.createNow(), fixture.matchId,
                            offerId, 3L)).join());
            fixture.service.rejectTradeOffer(fixture.guest, new RejectTradeOfferRequest(
                    MessageMetadata.createNow(), fixture.matchId, offerId, 3L)).join();
            assert fixture.stockpile(fixture.host).reservedAmountOf(ResourceType.FOOD) == 0;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void simultaneousDecisionsCannotDuplicateResources() {
        try (Fixture fixture = new Fixture(false)) {
            String offerId = fixture.create(fixture.host, fixture.guest, 3, 2, 1L).offer().offerId();
            fixture.endHostTurn(2L);
            ResourceStockpile sender = fixture.stockpile(fixture.host);
            ResourceStockpile recipient = fixture.stockpile(fixture.guest);
            int foodBefore = sender.amountOf(ResourceType.FOOD) + recipient.amountOf(ResourceType.FOOD);
            int woodBefore = sender.amountOf(ResourceType.WOOD) + recipient.amountOf(ResourceType.WOOD);
            AcceptTradeOfferRequest request = new AcceptTradeOfferRequest(MessageMetadata.createNow(),
                    fixture.matchId, offerId, 3L);
            CompletableFuture<?> first = fixture.service.acceptTradeOffer(fixture.guest, request);
            CompletableFuture<?> second = fixture.service.acceptTradeOffer(fixture.guest, request);
            int accepted = (first.handle((value, failure) -> failure == null ? 1 : 0).join()
                    + second.handle((value, failure) -> failure == null ? 1 : 0).join());
            assert accepted == 1;
            assert fixture.version() == 4L;
            assert sender.reservedAmountOf(ResourceType.FOOD) == 0;
            assert foodBefore == sender.amountOf(ResourceType.FOOD) + recipient.amountOf(ResourceType.FOOD);
            assert woodBefore == sender.amountOf(ResourceType.WOOD) + recipient.amountOf(ResourceType.WOOD);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void clientInboxUpdatesOnTheEdt() throws Exception {
        ClientStateStore store = new ClientStateStore(new SwingUiEventDispatcher());
        ClientMessageRouter router = new ClientMessageRouter(store, new SwingUiEventDispatcher());
        PlayerTradePanel panel = new PlayerTradePanel();
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicBoolean onEdt = new AtomicBoolean();
        store.addTradeOfferListener(offer -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            panel.displayOffer(offer); delivered.countDown();
        });
        TradeOfferPush push = new TradeOfferPush(MessageMetadata.createNow(), "match", 2L,
                "offer", "sender", "recipient", bundle("FOOD", 1), bundle("WOOD", 1),
                "PENDING", NOW);
        Thread listener = new Thread(() -> router.onPush(push), "test-trade-listener");
        listener.start(); listener.join(500L);
        assert !listener.isAlive();
        assert delivered.await(2, TimeUnit.SECONDS) && onEdt.get();
        assert panel.inbox().getModel().getSize() == 1;
        assert store.tradeOffers().get(0).offerId().equals("offer");
    }

    // PHASE 3 - Added in Phase 3
    private static ResourceBundleDto bundle(String type, long amount) {
        return new ResourceBundleDto(Map.of(type, amount));
    }

    // PHASE 3 - Added in Phase 3
    private static void expectTrade(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof PlayerTradeException : failure.getCause();
            assert ((PlayerTradeException) failure.getCause()).code().equals(code) : failure.getCause();
        }
    }

    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId recipient, PushMessage message) {}

    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> messages = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Trader A");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Trader B");
        // PHASE 3 - Added in Phase 3
        private final ClientSession observer;
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service = new LobbyServerService(
                (session, message) -> messages.add(new Sent(session, message)),
                civilization.server.map.PredefinedMapCatalog.standard(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final String matchId;

        // PHASE 3 - Added in Phase 3
        private Fixture(boolean withObserver) {
            observer = withObserver ? sessions.create("Observer") : null;
            String lobby = service.create(host).join().view().lobbyId().toString();
            service.join(guest, lobby).join();
            if (observer != null) service.join(observer, lobby).join();
            service.selectMap(host, lobby, "emerald-plains").join();
            service.ready(host, lobby, true).join();
            service.ready(guest, lobby, true).join();
            if (observer != null) service.ready(observer, lobby, true).join();
            matchId = service.start(host, lobby).join().launch().matchId();
        }

        // PHASE 3 - Added in Phase 3
        private civilization.server.trade.PlayerTradeResult create(ClientSession sender,
                ClientSession recipient, int food, int wood, long version) {
            return service.createTradeOffer(sender, request(sender, recipient,
                    bundle("FOOD", food), bundle("WOOD", wood), version)).join();
        }

        // PHASE 3 - Added in Phase 3
        private CreateTradeOfferRequest request(ClientSession sender, ClientSession recipient,
                ResourceBundleDto offered, ResourceBundleDto requested, long version) {
            return new CreateTradeOfferRequest(MessageMetadata.createNow(), matchId,
                    recipient.playerId().toString(), offered, requested, version);
        }

        // PHASE 3 - Added in Phase 3
        private void endHostTurn(long version) {
            service.endTurn(host, new EndTurnRequest(MessageMetadata.createNow(), matchId, version)).join();
        }

        // PHASE 3 - Added in Phase 3
        private ResourceStockpile stockpile(ClientSession session) {
            return service.match(matchId).project(state -> state.requirePlayer(session.playerId())
                    .empire().townHall().stockpile()).join();
        }

        // PHASE 3 - Added in Phase 3
        private long version() { return service.match(matchId).project(MultiplayerGameState::stateVersion).join(); }
        // PHASE 3 - Added in Phase 3
        private int countTrade(SessionId recipient) { return (int) messages.stream().filter(sent -> sent.recipient().equals(recipient) && sent.message() instanceof TradeOfferPush).count(); }
        // PHASE 3 - Added in Phase 3
        private TradeOfferPush latestTrade(SessionId recipient) { return (TradeOfferPush) messages.stream().filter(sent -> sent.recipient().equals(recipient) && sent.message() instanceof TradeOfferPush).reduce((first, second) -> second).orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        private GameStatePush latestState(SessionId recipient) { return (GameStatePush) messages.stream().filter(sent -> sent.recipient().equals(recipient) && sent.message() instanceof GameStatePush).reduce((first, second) -> second).orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }
}
