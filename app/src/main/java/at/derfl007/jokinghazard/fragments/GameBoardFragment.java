package at.derfl007.jokinghazard.fragments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.JsonToken;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import at.derfl007.jokinghazard.R;
import at.derfl007.jokinghazard.activities.MainActivity;
import io.socket.client.Ack;
import io.socket.client.Socket;

public class GameBoardFragment extends Fragment {

    private static final int HAS_CARD = 0;
    private Socket socket;

    enum PILES {
        DECK(0, new int[]{R.id.pileDeck}),
        PLAYER_1(1, new int[]{R.id.pilePlayer1, R.id.pilePlayer2, R.id.pilePlayer3, R.id.pilePlayer4, R.id.pilePlayer5, R.id.pilePlayer6, R.id.pilePlayer7, R.id.pilePlayer8}),
        PLAYER_2(2, new int[]{R.id.pilePlayer1, R.id.pilePlayer2, R.id.pilePlayer3, R.id.pilePlayer4, R.id.pilePlayer5, R.id.pilePlayer6, R.id.pilePlayer7, R.id.pilePlayer8}),
        PLAYER_3(3, new int[]{R.id.pilePlayer1, R.id.pilePlayer2, R.id.pilePlayer3, R.id.pilePlayer4, R.id.pilePlayer5, R.id.pilePlayer6, R.id.pilePlayer7, R.id.pilePlayer8}),
        PLAYER_4(4, new int[]{R.id.pilePlayer1, R.id.pilePlayer2, R.id.pilePlayer3, R.id.pilePlayer4, R.id.pilePlayer5, R.id.pilePlayer6, R.id.pilePlayer7, R.id.pilePlayer8}),
        PANEL_1(5, new int[]{R.id.pilePanel1}),
        PANEL_2(6, new int[]{R.id.pilePanel2}),
        PANEL_3(7, new int[]{R.id.pilePanel3}),
        SUBMISSION(8, new int[]{R.id.pileSubmission}),
        DISCARD(9, new int[]{R.id.pileSubmission});

        private final int id;
        private final int[] imageButtonIds;

        PILES(int id, int[] imageButtonIds) {
            this.imageButtonIds = imageButtonIds;
            this.id = id;
        }

        private static PILES getPileById(int id) {
            for (PILES pile : values()) {
                if (pile.id == id) return pile;
            }
            return null;
        }
    }

    private PILES playerPile;

    private static final String ARG_ROOM_CODE = "roomCode";

    private String roomCode;

    public GameBoardFragment() {
        // Required empty public constructor
    }

    public static GameBoardFragment newInstance(String roomCode) {
        GameBoardFragment fragment = new GameBoardFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_CODE, roomCode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            roomCode = getArguments().getString(ARG_ROOM_CODE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        socket = ((MainActivity) requireActivity()).mSocket;

        return inflater.inflate(R.layout.fragment_game_board, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String[] playerIds = new String[4];

        AtomicBoolean isAdmin = new AtomicBoolean(false);

        socket.emit("user:data:get", socket.id(), (Ack) response -> {
            JSONObject jsonResponse = (JSONObject) response[0];
            try {
                Log.d("RESPONSE", jsonResponse.getString("status") + ", " + jsonResponse.getString("msg"));
                isAdmin.set(jsonResponse.getJSONObject("userData").getBoolean("admin"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        for (int i1 = 0; i1 < 8; i1++) {
            ImageButton player1ImageButtonId1 = view.findViewById(PILES.PLAYER_1.imageButtonIds[i1]);
            player1ImageButtonId1.setEnabled(false);
            player1ImageButtonId1.setOnClickListener(null);
        }

        socket.emit("room:enteredGame");

        socket.on("room:ready_to_play", (obj) -> requireActivity().runOnUiThread(() -> {
            view.findViewById(R.id.overlay).setVisibility(View.GONE);
            socket.emit("room:players", (Ack) response -> requireActivity().runOnUiThread(() -> {
                JSONObject jsonResponse = (JSONObject) response[0];
                try {
                    Log.d("RESPONSE", jsonResponse.toString());
                    JSONArray players = jsonResponse.getJSONArray("users");

                    for (int i = 0; i < players.length(); i++) {
                        playerIds[i] = players.getJSONObject(i).getString("id");
                        if (playerIds[i].equals(socket.id())) {
                            this.playerPile = PILES.getPileById(i + 1);
                        }
                        // TODO Set user names
                    }

                    for (int i = 0; i < this.playerPile.imageButtonIds.length - 1; i++) {
                        moveCard(PILES.DECK.id, this.playerPile.id, 0, i);
                    }
                } catch (JSONException | ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
            }));
        }));

        socket.on("card:moved", (response) -> requireActivity().runOnUiThread(() -> cardMoved(view, response)));

        socket.on("room:your_turn", (response) -> requireActivity().runOnUiThread(() -> gameTurn(view, response)));

        socket.on("room:all_cards_played", args -> requireActivity().runOnUiThread(() -> {
            LinearLayout votingUi = view.findViewById(R.id.votingUiOverlay);
            votingUi.setVisibility(View.VISIBLE);
            // TODO Load cards into imageviews add points to winning player and tell the others who won the round (requires server changes)
//            socket.emit("user:points:add", <id>, <points>, (Ack) args -> {});
            votingUi.setVisibility(View.GONE);
            for (int i = 0; i < playerIds.length - 1; i++) {
                moveCard(PILES.SUBMISSION.id, PILES.DISCARD.id, i, 0);
            }
        }));
    }

    private void cardMoved(View view, Object[] response) {
        JSONObject jsonResponse = (JSONObject) response[0];
        try {
            Log.d("RESPONSE", jsonResponse.toString());
            PILES sourcePile = PILES.getPileById(jsonResponse.getInt("sourcePile"));
            PILES targetPile = PILES.getPileById(jsonResponse.getInt("targetPile"));
            int sourceIndex = jsonResponse.getInt("sourceIndex");
            int targetIndex = jsonResponse.getInt("targetIndex");
            String cardId = jsonResponse.getString("cardId");
            setImageButtonCard(view, sourcePile, "-1", sourceIndex);
            setImageButtonCard(view, targetPile, cardId, targetIndex);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void moveCard(int sourcePile, int targetPile, int sourceIndex, int targetIndex) {
        socket.emit("card:move", sourcePile, targetPile, sourceIndex, targetIndex, (Ack) response -> requireActivity().runOnUiThread(() -> {
            JSONObject jsonResponse = (JSONObject) response[0];
            Log.d("RESPONSE", jsonResponse.toString());
        }));
    }

    private void gameTurn(View view, Object[] response) {

        for (int i = 0; i < this.playerPile.imageButtonIds.length; i++) {
            ImageButton imageButton = view.findViewById(this.playerPile.imageButtonIds[i]);
            if (!(boolean) imageButton.getTag(HAS_CARD)) {
                moveCard(PILES.DECK.id, this.playerPile.id, 0, i);
            }
        }

        Snackbar.make(view, "Your turn!", Snackbar.LENGTH_SHORT).show();

        boolean judge = false;

        if (response.length > 0) {
            JSONObject jsonResponse = (JSONObject) response[0];

            try {
                judge = jsonResponse.getBoolean("judge");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (judge) {
            ImageButton deck = view.findViewById(R.id.pileDeck);
            deck.setEnabled(true);
            deck.setOnClickListener(v -> {
                // TODO Implement drag and drop here instead of the simple moveCard call
                moveCard(PILES.DECK.id, PILES.PANEL_1.id, 0, 0);
                deck.setEnabled(false);
                deck.setOnClickListener(null);

                for (int i = 0; i < 8; i++) {
                    ImageButton player1ImageButton = view.findViewById(PILES.PLAYER_1.imageButtonIds[i]);
                    int finalI = i;
                    player1ImageButton.setOnClickListener(v1 -> {
                        // TODO Implement drag and drop here instead of the simple moveCard call
                        moveCard(this.playerPile.id, PILES.PANEL_2.id, finalI, 0);
                        for (int i1 = 0; i1 < 8; i1++) {
                            ImageButton player1ImageButtonId1 = view.findViewById(PILES.PLAYER_1.imageButtonIds[i1]);
                            player1ImageButtonId1.setEnabled(false);
                            player1ImageButtonId1.setOnClickListener(null);
                        }
                        socket.emit("room:playerDone", (Ack) response2 -> requireActivity().runOnUiThread(() -> Log.d("RESPONSE", ((JSONObject) response2[0]).toString())));
                    });
                }
                for (int i = 0; i < 8; i++) {
                    ImageButton player1ImageButtonId = view.findViewById(PILES.PLAYER_1.imageButtonIds[i]);
                    player1ImageButtonId.setEnabled(true);
                }
            });

        } else {
            for (int i = 0; i < 8; i++) {
                ImageButton player1ImageButtonId = requireView().findViewById(PILES.PLAYER_1.imageButtonIds[i]);
                int finalI = i;
                player1ImageButtonId.setOnClickListener(v -> {
                    // TODO Implement drag and drop here instead of the simple moveCard call
                    moveCard(this.playerPile.id, PILES.SUBMISSION.id, finalI, 0);
                    for (int i1 = 0; i1 < 8; i1++) {
                        ImageButton player1ImageButtonId1 = view.findViewById(PILES.PLAYER_1.imageButtonIds[i1]);
                        player1ImageButtonId1.setEnabled(false);
                        player1ImageButtonId1.setOnClickListener(null);
                    }
                    socket.emit("room:playerDone", (Ack) response2 -> requireActivity().runOnUiThread(() -> Log.d("RESPONSE", ((JSONObject) response2[0]).toString())));
                });
            }
            for (int i = 0; i < 8; i++) {
                ImageButton player1ImageButtonId = requireView().findViewById(PILES.PLAYER_1.imageButtonIds[i]);
                player1ImageButtonId.setEnabled(true);
            }
        }
    }

    private void setImageButtonCard(View view, PILES pile, @NonNull String cardId, int index) {
        // If the pile is a player pile, check if it belongs to the player, otherwise do nothing
        if (pile != PILES.DECK && (pile.id < PILES.PLAYER_1.id || pile.id > PILES.PLAYER_4.id || pile.id == this.playerPile.id)) {
            ImageButton imageButton = view.findViewById(pile.imageButtonIds[index]);
            if (cardId.equals("-1")) {
                imageButton.setImageResource(R.drawable.transparent);
                imageButton.setTag(HAS_CARD, true);
            } else if (pile == PILES.SUBMISSION) {
                imageButton.setImageResource(R.drawable.back);
                imageButton.setTag(HAS_CARD, true);
            } else {
                imageButton.setImageResource(getCardImageById(view, cardId));
                imageButton.setTag(HAS_CARD, true);
            }
        }
    }

    private int getCardImageById(View view, String cardId) {
        Resources resources = view.getContext().getResources();
        return resources.getIdentifier("card_" + cardId, "drawable", view.getContext().getPackageName());
    }
}