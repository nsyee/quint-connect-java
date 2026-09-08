// Copyright 2026 the quint-connect-java authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Tictactoe drivers mirroring `TicTacToeDrivers` in the Java test sources.
//!
//! `Correct` follows the specification; `Diverging` forgets to hand the turn
//! back to X after `MoveO`, so the state check fails at the first `MoveO`.
//! The game model is a copy of `connect/examples/tictactoe/game.rs` upstream.

use quint_connect::*;
use serde::Deserialize;
use std::collections::BTreeMap;

#[derive(Default, Eq, PartialEq, Deserialize, Clone, Copy, Debug)]
#[serde(tag = "tag")]
pub enum Player {
    #[default]
    X,
    O,
}

pub type Position = (usize, usize);

#[derive(Default, Debug)]
pub struct TicTacToe {
    pub board: [[Option<Player>; 3]; 3],
    pub next_turn: Player,
}

impl TicTacToe {
    pub fn move_to(&mut self, pos: Position, player: Player) {
        let (x, y) = pos;
        self.board[y][x] = Some(player);
        self.next_turn = match player {
            Player::X => Player::O,
            Player::O => Player::X,
        }
    }
}

#[derive(Eq, PartialEq, Deserialize, Debug)]
#[serde(tag = "tag", content = "value")]
pub enum Square {
    Occupied(Player),
    Empty,
}

#[derive(Eq, PartialEq, Deserialize, Debug)]
pub struct GameState {
    pub board: BTreeMap<usize, BTreeMap<usize, Square>>,
    #[serde(rename = "nextTurn")]
    pub next_turn: Player,
}

fn board_of(game: &TicTacToe) -> BTreeMap<usize, BTreeMap<usize, Square>> {
    let mut board: BTreeMap<usize, BTreeMap<usize, Square>> = BTreeMap::new();
    for (col, x) in game.board.iter().zip(1..) {
        for (cell, y) in col.iter().zip(1..) {
            let square = cell.map(Square::Occupied).unwrap_or(Square::Empty);
            board.entry(y).or_default().insert(x, square);
        }
    }
    board
}

fn to_game_pos(pos: Position) -> Position {
    let (x, y) = pos;
    (x - 1, y - 1)
}

fn play(game: &mut TicTacToe, step: &Step) -> Result {
    switch!(step {
        init => *game = TicTacToe::default(),
        MoveX(corner?, coordinate?) => match corner.or(coordinate) {
            Some(pos) => game.move_to(to_game_pos(pos), Player::X),
            None => game.move_to((1, 1), Player::X)
        },
        MoveO(coordinate) => game.move_to(to_game_pos(coordinate), Player::O),
        stuttered => ()
    })
}

#[derive(Default)]
pub struct Correct {
    pub game: TicTacToe,
}

impl State<Correct> for GameState {
    fn from_driver(driver: &Correct) -> Result<Self> {
        Ok(Self {
            board: board_of(&driver.game),
            next_turn: driver.game.next_turn,
        })
    }
}

impl Driver for Correct {
    type State = GameState;

    fn step(&mut self, step: &Step) -> Result {
        play(&mut self.game, step)
    }
}

#[derive(Default)]
pub struct Diverging {
    pub game: TicTacToe,
}

impl State<Diverging> for GameState {
    fn from_driver(driver: &Diverging) -> Result<Self> {
        Ok(Self {
            board: board_of(&driver.game),
            next_turn: driver.game.next_turn,
        })
    }
}

impl Driver for Diverging {
    type State = GameState;

    fn step(&mut self, step: &Step) -> Result {
        play(&mut self.game, step)?;
        if step.action_taken == "MoveO" {
            self.game.next_turn = Player::O;
        }
        Ok(())
    }
}
